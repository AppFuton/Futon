package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

class FreeWebNovelRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.FREEWEBNOVEL) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			val encoded = URLEncoder.encode(query, "UTF-8")
			if (page > 1) {
				"$baseUrl/search?keyword=$encoded&page=$page"
			} else {
				"$baseUrl/search?keyword=$encoded"
			}
		} else {
			val sortPath = when (order) {
				SortOrder.NEWEST -> "latest-novel"
				SortOrder.UPDATED -> "latest-release"
				else -> "most-popular"
			}
			if (page > 1) {
				"$baseUrl/sort/$sortPath/$page"
			} else {
				"$baseUrl/sort/$sortPath"
			}
		}

		val doc = fetchDocument(url)
		val items = doc.select("div.li-row")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst("h3.tit a, .tit a") ?: return@mapNotNull null
			val title = titleEl.attr("title").ifEmpty { titleEl.text().trim() }
			if (title.isEmpty()) return@mapNotNull null
			val href = titleEl.absUrl("href").ifEmpty {
				val rel = titleEl.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}
			val coverEl = element.selectFirst("div.pic img, .pic img")
			val coverUrl = coverEl?.absUrl("src")?.ifEmpty { null } ?: coverEl?.attr("src")?.let {
				if (it.startsWith("/")) "$baseUrl$it" else "$baseUrl/$it"
			}.orEmpty()
			val desc = element.selectFirst("div.desc, .desc")?.text()?.trim()
			val rating = element.selectFirst("div.core span, .core span")?.text()?.toFloatOrNull() ?: 0f
			val tags = element.select("a[href*='/genre/']").mapNotNull {
				val tagText = it.text().trim()
				if (tagText.isNotEmpty()) MangaTag(tagText, tagText, source) else null
			}.toSet()

			Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href,
				rating = rating,
				contentRating = null,
				coverUrl = coverUrl,
				tags = tags,
				state = null,
				authors = emptySet(),
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)
		}
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val desc = doc.selectFirst("div.inner, .inner")?.text()?.trim() ?: manga.description
		val coverEl = doc.selectFirst("div.pic img, .pic img")
		val cover = coverEl?.absUrl("src")?.ifEmpty { null }
			?: coverEl?.attr("src")?.let { if (it.startsWith("/")) "$baseUrl$it" else "$baseUrl/$it" }
			?: manga.coverUrl
		val author = doc.selectFirst("meta[property=og:novel:author]")?.attr("content")
			?: doc.selectFirst("a[href^=/author/], a[href*='/author/']")?.text()?.trim()
		val authors = if (!author.isNullOrBlank()) setOf(author) else manga.authors

		val genres = doc.selectFirst("meta[property=og:novel:genre]")?.attr("content")
		val tags = if (!genres.isNullOrBlank()) {
			genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map {
				MangaTag(it, it, source)
			}.toSet()
		} else {
			doc.select("a[href*='/genre/']").mapNotNull {
				val tagText = it.text().trim()
				if (tagText.isNotEmpty()) MangaTag(tagText, tagText, source) else null
			}.toSet().ifEmpty { manga.tags }
		}

		val statusStr = doc.selectFirst("meta[property=og:novel:status]")?.attr("content").orEmpty()
		val state = when {
			statusStr.contains("completed", ignoreCase = true) || statusStr.contains("full", ignoreCase = true) ->
				MangaState.FINISHED
			statusStr.contains("ongoing", ignoreCase = true) ->
				MangaState.ONGOING
			else -> manga.state
		}

		val chapters = loadAllChapters(manga.url, doc)

		return manga.copy(
			description = desc,
			coverUrl = cover,
			largeCoverUrl = cover,
			authors = authors,
			tags = tags,
			state = state,
			chapters = chapters,
		)
	}

	private suspend fun loadAllChapters(novelUrl: String, doc: Document): List<MangaChapter> {
		val indexListPage = doc.selectFirst("#indexListPage")
		val totalChapters = indexListPage?.attr("data-total-chapters")?.toIntOrNull() ?: 0

		// If total chapters <= 40 or indexListPage not found, try parsing from DOM first
		if (totalChapters in 1..40 || indexListPage == null) {
			val domChapters = parseChaptersFromDom(doc)
			if (domChapters.isNotEmpty()) {
				return domChapters
			}
		}

		// Try AJAX endpoint
		val ajaxResult = runCatchingCancellable {
			loadChaptersViaAjax(novelUrl)
		}.getOrNull()

		return if (!ajaxResult.isNullOrEmpty()) {
			ajaxResult
		} else {
			parseChaptersFromDom(doc)
		}
	}

	private suspend fun loadChaptersViaAjax(novelUrl: String): List<MangaChapter> {
		val firstPageUrl = "$novelUrl?ajax=chapters&page=1&pageSize=200"
		val firstJsonStr = fetchString(
			url = firstPageUrl,
			headers = mapOf(
				"Accept" to "application/json, text/javascript, */*; q=0.01",
				"X-Requested-With" to "XMLHttpRequest",
				"Referer" to novelUrl,
			),
		)
		val firstJson = JSONObject(firstJsonStr)
		if (firstJson.optInt("code", 0) != 200) {
			return emptyList()
		}

		val totalPages = firstJson.optInt("totalPage", 1)
		val firstHtml = firstJson.optString("html", "")
		val allChapters = parseChapterElementsFromHtml(firstHtml).toMutableList()

		if (totalPages > 1) {
			val semaphore = Semaphore(MAX_PARALLELISM)
			val remainingPages = (2..totalPages.coerceAtMost(MAX_CHAPTER_PAGES)).toList()
			val additionalPages = coroutineScope {
				remainingPages.map { pageNum ->
					async(Dispatchers.IO) {
						semaphore.withPermit {
							runCatchingCancellable {
								val pageUrl = "$novelUrl?ajax=chapters&page=$pageNum&pageSize=200"
								val jsonStr = fetchString(
									url = pageUrl,
									headers = mapOf(
										"Accept" to "application/json, text/javascript, */*; q=0.01",
										"X-Requested-With" to "XMLHttpRequest",
										"Referer" to novelUrl,
									),
								)
								val json = JSONObject(jsonStr)
								val html = json.optString("html", "")
								pageNum to parseChapterElementsFromHtml(html)
							}.getOrNull()
						}
					}
				}.mapNotNull { it.await() }
			}

			additionalPages.sortedBy { it.first }.forEach { (_, chapters) ->
				allChapters.addAll(chapters)
			}
		}

		return allChapters.mapIndexed { index, (chapTitle, chapUrl) ->
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

	private fun parseChaptersFromDom(doc: Document): List<MangaChapter> {
		val chapterElements = doc.select("#idData li a").ifEmpty {
			doc.select("ul.ul-list5:last-of-type li a").ifEmpty {
				doc.select("ul.ul-list5 li a")
			}
		}

		return chapterElements.mapIndexed { index, a ->
			val chapTitle = a.attr("title").ifEmpty { a.text().trim() }
			val chapUrl = a.absUrl("href").ifEmpty {
				val rel = a.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}
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

	private fun parseChapterElementsFromHtml(html: String): List<Pair<String, String>> {
		if (html.isEmpty()) return emptyList()
		val fragment = Jsoup.parseBodyFragment(html, baseUrl)
		return fragment.select("li a, a").mapNotNull { a ->
			val chapTitle = a.attr("title").ifEmpty { a.text().trim() }
			val chapUrl = a.absUrl("href").ifEmpty {
				val rel = a.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}
			if (chapUrl.isNotEmpty()) chapTitle to chapUrl else null
		}
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(chapter.url)
		val article = doc.selectFirst("div#article, #article") ?: doc.body()
		// Remove ads, scripts, and navigation
		article.select(".ad-box, script, style, iframe, .m-page, .m-nav, a#prev_url, a#next_url, .reader-ad-skip, [id^=bg-ssp], .comments-widget, #e-comments").remove()
		val contentHtml = article.html()

		return listOf(
			MangaPage(
				id = chapter.id,
				url = contentHtml,
				preview = null,
				source = source,
			)
		)
	}

	companion object {
		private const val MAX_PARALLELISM = 4
		private const val MAX_CHAPTER_PAGES = 50
	}
}
