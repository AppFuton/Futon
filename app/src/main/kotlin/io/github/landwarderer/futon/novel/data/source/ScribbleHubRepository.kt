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

class ScribbleHubRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.SCRIBBLEHUB) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			"$baseUrl/?s=${URLEncoder.encode(query, "UTF-8")}&post_type=fictionposts&paged=$page"
		} else {
			val sort = when (order) {
				SortOrder.NEWEST -> "date_added"
				SortOrder.UPDATED -> "last_updated"
				else -> "pageviews"
			}
			"$baseUrl/series-finder/?sf=1&sort=$sort&order=desc&paged=$page"
		}

		val doc = fetchDocument(url)
		val items = doc.select(".search_main_box")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst(".search_title a") ?: return@mapNotNull null
			val title = titleEl.text().trim()
			val href = titleEl.absUrl("href")

			val coverEl = element.selectFirst(".search_img img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()
			val desc = element.selectFirst(".search_body")?.text()?.trim()
			val tags = element.select(".search_genre a").map {
				MangaTag(title = it.text().trim(), key = it.text().trim(), source = source)
			}.toSet()

			Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitle = null,
				url = href,
				publicUrl = href,
				rating = 0f,
				isNsfw = false,
				coverUrl = coverUrl,
				tags = tags,
				state = null,
				author = null,
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val cover = doc.selectFirst(".fic_image img")?.absUrl("src") ?: manga.coverUrl
		val desc = doc.selectFirst(".wi_fic_desc")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst(".auth_name_fic")?.text()?.trim()

		val chapterLinks = doc.select(".toc_ol li a, #toc li a, .chapter-item a")
		val chapters = chapterLinks.mapIndexed { index, a ->
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
		val content = doc.selectFirst("#chp_raw, .chp_raw") ?: doc.body()
		content.select("script, style, iframe, .modern-footnotes-footnote").remove()
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
