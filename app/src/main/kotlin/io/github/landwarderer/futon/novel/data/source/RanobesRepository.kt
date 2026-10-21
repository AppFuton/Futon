package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.net.URLEncoder

class RanobesRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.RANOBES) {

	private val baseUrl = source.defaultUrl

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = false,
	)

	companion object {
		private const val MAX_CHAPTER_PAGES = 50
		private const val MAX_PARALLELISM = 4
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val effectiveOrder = order ?: defaultSortOrder

		val url = if (query.isNotEmpty()) {
			val encoded = URLEncoder.encode(query, "UTF-8")
			if (page > 1) {
				"$baseUrl/search/$encoded/page/$page/"
			} else {
				"$baseUrl/search/$encoded/"
			}
		} else {
			when (effectiveOrder) {
				SortOrder.UPDATED -> {
					if (page > 1) "$baseUrl/updates/page/$page/" else "$baseUrl/updates/"
				}
				SortOrder.POPULARITY -> {
					if (page > 1) return emptyList()
					"$baseUrl/ranking/"
				}
				else -> {
					if (page > 1) "$baseUrl/novels/page/$page/" else "$baseUrl/novels/"
				}
			}
		}

		val doc = fetchDocument(url)
		val items = doc.select(".rank-story, article.story, .shortstory, .story_line")
		return items.mapNotNull { parseNovelItem(it) }
	}

	private fun parseNovelItem(element: Element): Manga? {
		val isStoryLine = element.hasClass("story_line") || element.hasClass("story_line-img")
		if (isStoryLine) {
			val linkEl = element.selectFirst("a[href]") ?: return null
			val rawHref = linkEl.absUrl("href").ifEmpty {
				val rel = linkEl.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}
			val title = element.selectFirst("h3.title, .title")?.text()?.trim()
				?: linkEl.attr("title").trim()
			if (title.isEmpty()) return null

			// updates links point to chapters: /{slug}-{bookId}/{chapterId}.html
			// Convert to novel link: /novels/{bookId}-{slug}.html
			val chapterMatch = Regex("""/([^/]+)-(\d+)/\d+\.html""").find(rawHref)
			val novelUrl = if (chapterMatch != null) {
				"$baseUrl/novels/${chapterMatch.groupValues[2]}-${chapterMatch.groupValues[1]}.html"
			} else {
				rawHref
			}

			val coverUrl = extractCoverUrl(element)
			val subtitle = element.selectFirst(".subtitle")?.text()?.trim()
			val time = element.selectFirst("em.small, time")?.text()?.trim()
			val desc = listOfNotNull(subtitle, time).joinToString(" ").ifBlank { null }

			return Manga(
				id = "${source.name}_$novelUrl".longHashCode(),
				title = title,
				altTitles = emptySet(),
				url = novelUrl,
				publicUrl = novelUrl,
				rating = 0f,
				contentRating = null,
				coverUrl = coverUrl,
				tags = emptySet<MangaTag>(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)
		}

		val titleEl = element.selectFirst(".title a, h2 a, h3 a") ?: return null
		val title = titleEl.text().trim()
		if (title.isEmpty()) return null

		val href = titleEl.absUrl("href").ifEmpty {
			val rel = titleEl.attr("href")
			if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
		}
		val coverUrl = extractCoverUrl(element)
		val desc = element.selectFirst(".moreless__short, .moreless__full, .story, .description")?.text()?.trim()

		return Manga(
			id = "${source.name}_$href".longHashCode(),
			title = title,
			altTitles = emptySet(),
			url = href,
			publicUrl = href,
			rating = 0f,
			contentRating = null,
			coverUrl = coverUrl,
			tags = emptySet<MangaTag>(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = coverUrl,
			description = desc,
			chapters = null,
			source = source,
		)
	}

	private fun extractCoverUrl(element: Element): String {
		val img = element.selectFirst("img[src]")
		val src = img?.absUrl("src")?.ifEmpty { img.attr("src") }
		if (!src.isNullOrEmpty()) {
			return normalizeCoverUrl(src)
		}
		val styleEl = element.selectFirst("[style*='background-image'], [style*='background']")
		val style = styleEl?.attr("style").orEmpty()
		val match = Regex("""url\(['"]?(.*?)['"]?\)""").find(style)
		if (match != null) {
			val raw = match.groupValues[1]
			val abs = when {
				raw.startsWith("http://") || raw.startsWith("https://") -> raw
				raw.startsWith("//") -> "https:$raw"
				raw.startsWith("/") -> "$baseUrl$raw"
				else -> "$baseUrl/$raw"
			}
			return normalizeCoverUrl(abs)
		}
		return ""
	}

	private fun normalizeCoverUrl(url: String): String {
		val resolved = if (url.startsWith("//")) "https:$url" else url
		return resolved.replace("/thumbs/", "/")
	}

	private fun sanitizeUrl(url: String): String {
		return url.replace("ranobes.top", "ranobes.net")
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val novelUrl = sanitizeUrl(manga.url)
		val doc = fetchDocument(novelUrl)

		val posterEl = doc.selectFirst(".poster, .show-poster, .r-fullstory-poster") ?: doc
		val cover = extractCoverUrl(posterEl).ifEmpty { manga.coverUrl }
		val desc = doc.selectFirst(".moreless__full, #fs-info .moreless, .story, #fs-info")?.text()?.trim() ?: manga.description

		val authors = doc.select("a[href*='/authors/'], a[href*='/author/'], a[href*='/translater/']")
			.map { it.text().trim() }
			.filter { it.isNotEmpty() }
			.toSet()

		// Extract book id to load full chapters list
		val bookId = Regex("""/novels/(\d+)-""").find(novelUrl)?.groupValues?.get(1)
		val chaptersLink = doc.selectFirst("a[href*='/chapters/']")?.absUrl("href")
		val chaptersUrl = chaptersLink ?: if (bookId != null) "$baseUrl/chapters/$bookId/" else null

		val chapters = if (chaptersUrl != null) {
			loadAllChapters(chaptersUrl, doc)
		} else {
			parseChaptersFromDom(doc)
		}

		return manga.copy(
			url = novelUrl,
			publicUrl = novelUrl,
			description = desc,
			coverUrl = cover,
			largeCoverUrl = cover,
			authors = authors.ifEmpty { manga.authors },
			chapters = chapters,
		)
	}

	private suspend fun loadAllChapters(firstPageUrl: String, mainDoc: Element): List<MangaChapter> {
		val firstDoc = runCatching { fetchDocument(firstPageUrl) }.getOrNull() ?: return parseChaptersFromDom(mainDoc)
		val script = firstDoc.selectFirst("script:containsData(window.__DATA__)")?.data().orEmpty()
		val match = Regex("""window\.__DATA__\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(script)
			?: return parseChaptersFromDom(firstDoc).ifEmpty { parseChaptersFromDom(mainDoc) }

		val firstJson = runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
			?: return parseChaptersFromDom(firstDoc).ifEmpty { parseChaptersFromDom(mainDoc) }

		val pagesCount = firstJson.optInt("pages_count", 1)
		val bookId = firstJson.optInt("book_id", 0)

		val allRawChapters = mutableListOf<RawChapter>()
		// First page chapters
		allRawChapters.addAll(parseRawChaptersFromJson(firstJson))

		if (pagesCount > 1 && bookId > 0) {
			val remainingPages = (2..pagesCount.coerceAtMost(MAX_CHAPTER_PAGES)).toList()
			val semaphore = Semaphore(MAX_PARALLELISM)
			val additionalChapters = coroutineScope {
				remainingPages.map { pageNum ->
					async(Dispatchers.IO) {
						semaphore.withPermit {
							val pageUrl = "$baseUrl/chapters/$bookId/page/$pageNum/"
							runCatching {
								val pageDoc = fetchDocument(pageUrl)
								val pageScript = pageDoc.selectFirst("script:containsData(window.__DATA__)")?.data().orEmpty()
								val pageMatch = Regex("""window\.__DATA__\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(pageScript)
								if (pageMatch != null) {
									val json = JSONObject(pageMatch.groupValues[1])
									parseRawChaptersFromJson(json)
								} else {
									emptyList()
								}
							}.getOrDefault(emptyList())
						}
					}
				}.awaitAll()
			}
			additionalChapters.forEach { allRawChapters.addAll(it) }
		}

		// Ranobes presents newest chapters first on lower pages down to oldest chapters on higher pages.
		// Reverse the list so it is in ascending chronological order: Chapter 1 to Chapter N.
		val chronological = allRawChapters.reversed()
		return chronological.mapIndexed { index, raw ->
			MangaChapter(
				id = "${source.name}_${raw.link}".longHashCode(),
				title = raw.title,
				number = (index + 1).toFloat(),
				volume = 0,
				url = raw.link,
				uploadDate = raw.date * 1000L,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	private data class RawChapter(
		val id: Long,
		val title: String,
		val link: String,
		val date: Long,
	)

	private fun parseRawChaptersFromJson(json: JSONObject): List<RawChapter> {
		val array = json.optJSONArray("chapters") ?: return emptyList()
		val result = mutableListOf<RawChapter>()
		for (i in 0 until array.length()) {
			val obj = array.optJSONObject(i) ?: continue
			val link = obj.optString("link").trim()
			if (link.isEmpty()) continue
			val title = obj.optString("title").trim().ifEmpty { "Chapter" }
			val id = obj.optLong("id", 0L)
			val date = obj.optLong("date", 0L)
			result.add(RawChapter(id = id, title = title, link = link, date = date))
		}
		return result
	}

	private fun parseChaptersFromDom(doc: Element): List<MangaChapter> {
		val chapterLinks = doc.select("#chapters-list a, .chapters-scroll a, .chapters-list a, a.chapter-item")
		return chapterLinks.mapIndexed { index, a ->
			val rawHref = a.absUrl("href").ifEmpty { a.attr("href") }
			val chapUrl = sanitizeUrl(rawHref)
			val chapTitle = a.text().trim().ifEmpty { "Chapter ${index + 1}" }
			MangaChapter(
				id = "${source.name}_$chapUrl".longHashCode(),
				title = chapTitle,
				number = (index + 1).toFloat(),
				volume = 0,
				url = chapUrl,
				uploadDate = 0L,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val url = sanitizeUrl(chapter.url)
		val doc = fetchDocument(url)
		val content = doc.selectFirst("#arrticle, #article, .entry-content") ?: doc.body()
		content.select("script, style, iframe, .ads, .share, .free-support-top, [id^=bg-ssp]").remove()
		val contentHtml = content.html()

		return listOf(
			MangaPage(
				id = chapter.id,
				url = contentHtml,
				preview = null,
				source = source,
			)
		)
	}
}
