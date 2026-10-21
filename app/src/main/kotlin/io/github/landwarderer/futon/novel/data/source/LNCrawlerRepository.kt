package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.net.URLEncoder

class LNCrawlerRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.LNCRAWLER) {

	val baseUrl = source.defaultUrl
	val apiUrl = "https://api.lncrawler.monster"
	val sitemapUrl = "$baseUrl/sitemap.xml"

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.RATING,
		SortOrder.ALPHABETICAL,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		return runCatchingCancellable {
			fetchFromApi(offset, order, filter)
		}.getOrElse { error ->
			error.printStackTraceDebug("LNCrawlerRepository")
			// Fallback: check sitemap index if API fails
			fetchFromSitemapFallback(offset)
		}
	}

	private suspend fun fetchFromApi(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / PAGE_SIZE) + 1
		val query = filter?.query?.trim().orEmpty()
		val sortKey = mapSortKey(order)
		val sortDirection = mapSortOrder(order)

		val url = if (query.isNotEmpty()) {
			val encoded = URLEncoder.encode(query, "UTF-8")
			"$apiUrl/novels/search/?query=$encoded&sort_by=$sortKey&sort_order=$sortDirection&page=$page&page_size=$PAGE_SIZE"
		} else {
			"$apiUrl/novels/search/?sort_by=$sortKey&sort_order=$sortDirection&page=$page&page_size=$PAGE_SIZE"
		}

		val jsonStr = fetchString(url)
		val json = JSONObject(jsonStr)
		val results = json.optJSONArray("results") ?: JSONArray()
		val mangas = ArrayList<Manga>(results.length())

		for (i in 0 until results.length()) {
			val item = results.getJSONObject(i)
			val title = item.optString("title").trim()
			val slug = item.optString("slug").trim()
			if (title.isEmpty() || slug.isEmpty()) continue

			val novelUrl = "$baseUrl/novels/$slug/"
			val prefSource = item.optJSONObject("prefered_source")
			val coverUrl = prefSource?.optString("cover_min_url")?.ifEmpty { null }
				?: prefSource?.optString("cover_url")?.ifEmpty { null }.orEmpty()
			val rating = item.optDouble("avg_rating", 0.0).toFloat()

			val authors = buildSet {
				prefSource?.optJSONArray("authors")?.let { arr ->
					for (j in 0 until arr.length()) {
						val author = arr.optString(j).trim()
						if (author.isNotEmpty()) add(author)
					}
				}
			}

			val tags = buildSet {
				prefSource?.optJSONArray("tags")?.let { arr ->
					for (j in 0 until arr.length()) {
						val tag = arr.optString(j).trim()
						if (tag.isNotEmpty()) add(MangaTag(tag, tag, source))
					}
				}
			}

			mangas.add(
				Manga(
					id = "${source.name}_$novelUrl".longHashCode(),
					title = title,
					altTitles = emptySet(),
					url = novelUrl,
					publicUrl = novelUrl,
					rating = rating,
					contentRating = null,
					coverUrl = coverUrl,
					tags = tags,
					state = null,
					authors = authors,
					largeCoverUrl = coverUrl,
					description = null,
					chapters = null,
					source = source,
				)
			)
		}
		return mangas
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val (novelSlug, sourceSlug) = parseNovelAndSourceSlugs(manga.url)
		check(novelSlug.isNotEmpty()) { "Invalid novel slug from URL: ${manga.url}" }

		val novelJson = JSONObject(fetchString("$apiUrl/novels/$novelSlug/"))
		val title = novelJson.optString("title").trim().ifEmpty { manga.title }
		val rating = novelJson.optDouble("avg_rating", manga.rating.toDouble()).toFloat()

		val prefSource = novelJson.optJSONObject("prefered_source")
		val sourcesArr = novelJson.optJSONArray("sources")
		val targetSourceSlug = sourceSlug
			?: prefSource?.optString("source_slug")?.ifEmpty { null }
			?: (if (sourcesArr != null && sourcesArr.length() > 0) sourcesArr.getJSONObject(0).optString("source_slug") else null)

		if (targetSourceSlug.isNullOrEmpty()) {
			return manga.copy(title = title, rating = rating, chapters = emptyList())
		}

		val sourceDetailJson = JSONObject(fetchString("$apiUrl/novels/$novelSlug/$targetSourceSlug/"))
		val rawSynopsis = sourceDetailJson.optString("synopsis")
		val desc = if (rawSynopsis.isNotEmpty()) {
			Jsoup.parse(rawSynopsis).text().trim()
		} else {
			manga.description
		}

		val cover = sourceDetailJson.optString("cover_url").ifEmpty {
			prefSource?.optString("cover_url")?.ifEmpty { null } ?: manga.coverUrl
		}

		val authors = buildSet {
			sourceDetailJson.optJSONArray("authors")?.let { arr ->
				for (i in 0 until arr.length()) {
					val a = arr.optString(i).trim()
					if (a.isNotEmpty()) add(a)
				}
			}
		}.ifEmpty { manga.authors }

		val tags = buildSet {
			sourceDetailJson.optJSONArray("tags")?.let { arr ->
				for (i in 0 until arr.length()) {
					val t = arr.optString(i).trim()
					if (t.isNotEmpty()) add(MangaTag(t, t, source))
				}
			}
		}.ifEmpty { manga.tags }

		val statusStr = sourceDetailJson.optString("status")
		val state = when {
			statusStr.contains("Completed", ignoreCase = true) -> MangaState.FINISHED
			statusStr.contains("Ongoing", ignoreCase = true) -> MangaState.ONGOING
			statusStr.contains("Hiatus", ignoreCase = true) -> MangaState.ABANDONED
			else -> manga.state
		}

		val sourceName = sourceDetailJson.optString("source_name").ifEmpty { targetSourceSlug }
		val firstPageJson = JSONObject(fetchString("$apiUrl/novels/$novelSlug/$targetSourceSlug/chapters/?page=1&page_size=500"))
		val totalPages = firstPageJson.optInt("total_pages", 1)

		val allChapterObjs = ArrayList<JSONObject>()
		firstPageJson.optJSONArray("chapters")?.let { arr ->
			for (i in 0 until arr.length()) {
				allChapterObjs.add(arr.getJSONObject(i))
			}
		}

		if (totalPages > 1) {
			val remainingPages = coroutineScope {
				(2..totalPages).map { p ->
					async {
						runCatching {
							val pJson = JSONObject(fetchString("$apiUrl/novels/$novelSlug/$targetSourceSlug/chapters/?page=$p&page_size=500"))
							val pArr = pJson.optJSONArray("chapters") ?: JSONArray()
							(0 until pArr.length()).map { pArr.getJSONObject(it) }
						}.getOrDefault(emptyList())
					}
				}.awaitAll().flatten()
			}
			allChapterObjs.addAll(remainingPages)
		}

		val chapters = allChapterObjs.mapIndexed { index, c ->
			val chapterId = c.optInt("chapter_id", index + 1)
			val chapTitle = c.optString("title").trim().ifEmpty { "Chapter $chapterId" }
			val chapUrl = "$apiUrl/novels/$novelSlug/$targetSourceSlug/chapter/$chapterId/"
			val volume = c.optInt("volume", 0)

			MangaChapter(
				id = "${source.name}_${novelSlug}_${targetSourceSlug}_$chapterId".longHashCode(),
				title = chapTitle,
				number = chapterId.toFloat(),
				volume = volume,
				url = chapUrl,
				uploadDate = 0L,
				source = source,
				scanlator = sourceName,
				branch = sourceName,
			)
		}

		return manga.copy(
			title = title,
			rating = rating,
			description = desc,
			coverUrl = cover,
			largeCoverUrl = cover,
			authors = authors,
			tags = tags,
			state = state,
			chapters = chapters,
		)
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val json = JSONObject(fetchString(chapter.url))
		val bodyHtml = json.optString("body")
		val doc = Jsoup.parseBodyFragment(bodyHtml)
		doc.select("script, style, iframe").remove()
		val cleanHtml = doc.body().html()

		return listOf(
			MangaPage(
				id = chapter.id,
				url = cleanHtml,
				preview = null,
				source = source,
			)
		)
	}

	suspend fun fetchSitemapIndex(): List<String> = withContext(Dispatchers.IO) {
		val xml = fetchString(sitemapUrl)
		val doc = Jsoup.parse(xml, "", Parser.xmlParser())
		doc.select("sitemap loc").map { it.text().trim() }.filter { it.isNotEmpty() }
	}

	suspend fun fetchSitemapNovels(sitemapNovelUrl: String = "$baseUrl/sitemap-novels.xml"): List<String> = withContext(Dispatchers.IO) {
		val xml = fetchString(sitemapNovelUrl)
		val doc = Jsoup.parse(xml, "", Parser.xmlParser())
		doc.select("url loc").map { it.text().trim() }.filter { it.isNotEmpty() }
	}

	private suspend fun fetchFromSitemapFallback(offset: Int): List<Manga> {
		val sitemapPage = (offset / 1000) + 1
		val sitemapNovelUrl = if (sitemapPage == 1) "$baseUrl/sitemap-novels.xml" else "$baseUrl/sitemap-novels.xml?p=$sitemapPage"
		val urls = runCatchingCancellable { fetchSitemapNovels(sitemapNovelUrl) }.getOrDefault(emptyList())
		val subList = urls.drop(offset % 1000).take(PAGE_SIZE)
		return subList.map { url ->
			val slug = url.trimEnd('/').substringAfterLast('/')
			val title = slug.replace('-', ' ').split(' ')
				.joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
			Manga(
				id = "${source.name}_$url".longHashCode(),
				title = title,
				altTitles = emptySet(),
				url = url,
				publicUrl = url,
				rating = 0f,
				contentRating = null,
				coverUrl = "",
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = "",
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	fun parseNovelAndSourceSlugs(url: String): Pair<String, String?> {
		val path = url.substringAfter("://").substringAfter('/')
		val segments = path.split('/').filter { it.isNotEmpty() }
		val novelIndex = segments.indexOf("novels")
		if (novelIndex != -1 && novelIndex + 1 < segments.size) {
			val novelSlug = segments[novelIndex + 1]
			val potentialSource = segments.getOrNull(novelIndex + 2)
			val sourceSlug = if (potentialSource != null && potentialSource != "chapterlist" && potentialSource != "chapters") {
				potentialSource
			} else {
				null
			}
			return novelSlug to sourceSlug
		}
		val cleaned = url.trimEnd('/').substringAfterLast('/')
		return cleaned to null
	}

	private fun mapSortKey(order: SortOrder?): String = when (order) {
		SortOrder.POPULARITY -> "popularity"
		SortOrder.UPDATED -> "last_updated"
		SortOrder.NEWEST -> "date_added"
		SortOrder.RATING -> "rating"
		SortOrder.ALPHABETICAL -> "title"
		else -> "popularity"
	}

	private fun mapSortOrder(order: SortOrder?): String = when (order) {
		SortOrder.ALPHABETICAL -> "asc"
		else -> "desc"
	}

	companion object {
		private const val PAGE_SIZE = 20
	}
}
