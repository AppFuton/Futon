package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.net.URLEncoder

class ScribbleHubRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.SCRIBBLEHUB) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			val encoded = URLEncoder.encode(query, "UTF-8")
			"$baseUrl/series-finder/?sf=1&sh=$encoded&sort=pageviews&order=desc&pg=$page"
		} else {
			val sort = when (order) {
				SortOrder.NEWEST -> "dateadded"
				SortOrder.UPDATED -> "lastchpdate"
				SortOrder.RATING -> "ratings"
				else -> "pageviews"
			}
			"$baseUrl/series-finder/?sf=1&sort=$sort&order=desc&pg=$page"
		}

		val doc = fetchDocument(url, getScribbleHeaders(referer = "$baseUrl/"))
		val items = doc.select(".search_main_box")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst(".search_title a") ?: return@mapNotNull null
			val title = titleEl.text().trim()
			if (title.isEmpty()) return@mapNotNull null
			val href = titleEl.absUrl("href").ifEmpty {
				val rel = titleEl.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}

			val coverEl = element.selectFirst(".search_img img")
			val coverUrl = coverEl?.absUrl("src")?.ifEmpty { null } ?: coverEl?.attr("src")?.let {
				if (it.startsWith("/")) "$baseUrl$it" else "$baseUrl/$it"
			}.orEmpty()

			val ratingText = element.selectFirst(".search_ratings")?.text()?.trim().orEmpty()
			val rating = ratingText.removeSurrounding("(", ")").toFloatOrNull() ?: 0f

			val author = element.selectFirst("span[title='Author'] a, .search_stats .a_un_st a")?.text()?.trim()
			val authors = if (!author.isNullOrBlank()) setOf(author) else emptySet()

			val tags = element.select(".search_genre a").mapNotNull {
				val tagText = it.text().trim()
				if (tagText.isNotEmpty()) MangaTag(tagText, tagText, source) else null
			}.toSet()

			val bodyClone = element.selectFirst(".search_body")?.clone()
			bodyClone?.select(".search_title, .search_stats, .search_genre, .testhide, .morelink, .dots")?.remove()
			val desc = bodyClone?.text()?.trim()

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
				authors = authors,
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)
		}
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(
			manga.url,
			getScribbleHeaders(
				referer = "$baseUrl/",
				cookie = "toc_show=10000; toc_sorder=asc",
			),
		)
		val cover = doc.selectFirst(".fic_image img")?.absUrl("src")?.ifEmpty { null }
			?: doc.selectFirst(".fic_image img")?.attr("src")
			?: manga.coverUrl
		val desc = doc.selectFirst(".wi_fic_desc")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst(".auth_name_fic")?.text()?.trim()
		val authors = if (!author.isNullOrBlank()) setOf(author) else manga.authors

		val genreTags = doc.select(".fic_genre, .stag").mapNotNull {
			val tagText = it.text().trim()
			if (tagText.isNotEmpty()) MangaTag(tagText, tagText, source) else null
		}.toSet()
		val tags = genreTags.ifEmpty { manga.tags }

		val statusText = doc.select(".wi_fic_show_stats, .fic_stats").text()
		val state = when {
			statusText.contains("Completed", ignoreCase = true) -> MangaState.FINISHED
			statusText.contains("Ongoing", ignoreCase = true) -> MangaState.ONGOING
			statusText.contains("Hiatus", ignoreCase = true) -> MangaState.ABANDONED
			else -> manga.state
		}

		val chapterLinks = doc.select(".toc_ol li a.toc_a, .toc_ol li a, .wi_fic_table.toc a")
		val chapters = chapterLinks.mapIndexed { index, a ->
			val chapUrl = a.absUrl("href").ifEmpty {
				val rel = a.attr("href")
				if (rel.startsWith("/")) "$baseUrl$rel" else "$baseUrl/$rel"
			}
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

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(
			chapter.url,
			getScribbleHeaders(referer = "$baseUrl/"),
		)
		val content = doc.selectFirst("#chp_raw, .chp_raw") ?: doc.body()
		content.select("script, style, iframe, .modern-footnotes-footnote, .sp-head").remove()
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

	private fun getScribbleHeaders(
		referer: String? = null,
		cookie: String? = null,
	): Map<String, String> {
		val headers = mutableMapOf(
			"User-Agent" to SCRIBBLE_USER_AGENT,
			"Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
			"Accept-Language" to "en-US,en;q=0.5",
		)
		if (referer != null) {
			headers["Referer"] = referer
		}
		if (cookie != null) {
			headers["Cookie"] = cookie
		}
		return headers
	}

	companion object {
		private const val SCRIBBLE_USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"
	}
}
