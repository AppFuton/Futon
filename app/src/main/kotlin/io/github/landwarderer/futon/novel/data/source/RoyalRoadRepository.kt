package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.net.URLEncoder

class RoyalRoadRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.ROYALROAD) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			"$baseUrl/fictions/search?title=${URLEncoder.encode(query, "UTF-8")}&page=$page"
		} else {
			val sortPath = when (order) {
				SortOrder.NEWEST -> "new"
				SortOrder.UPDATED -> "latest-updates"
				else -> "best-rated"
			}
			"$baseUrl/fictions/$sortPath?page=$page"
		}

		val doc = fetchDocument(url)
		val items = doc.select(".fiction-list-item")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst("h2.fiction-title a") ?: return@mapNotNull null
			val title = titleEl.text().trim()
			val href = titleEl.absUrl("href")

			val coverEl = element.selectFirst("img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()
			val desc = element.selectFirst(".fiction-description")?.text()?.trim()
			val author = element.selectFirst("span.author, a[href*='/profile/']")?.text()?.trim()
			val tags = element.select(".fiction-tag").map { MangaTag(title = it.text().trim(), key = it.text().trim(), source = source) }.toSet()

			val ratingStr = element.selectFirst(".star")?.attr("title")
			val rating = ratingStr?.toFloatOrNull() ?: 0f

			Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitle = null,
				url = href,
				publicUrl = href,
				rating = rating,
				isNsfw = false,
				coverUrl = coverUrl,
				tags = tags,
				state = null,
				author = author,
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val cover = doc.selectFirst(".cover-art-container img, img.cover")?.absUrl("src") ?: manga.coverUrl
		val desc = doc.selectFirst(".description .portlet-body")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst("h4 a[href*='/profile/']")?.text()?.trim() ?: manga.author

		val chapterRows = doc.select("#chapters tbody tr")
		val chapters = chapterRows.mapIndexedNotNull { index, tr ->
			val a = tr.selectFirst("a[href]") ?: return@mapIndexedNotNull null
			val chapUrl = a.absUrl("href")
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
			authors = author?.let { setOf(it) } ?: manga.authors,
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(chapter.url)
		val content = doc.selectFirst(".chapter-content") ?: doc.body()
		// Clean up
		content.select("script, style, .portlet-title, .author-note-portlet .btn").remove()
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
