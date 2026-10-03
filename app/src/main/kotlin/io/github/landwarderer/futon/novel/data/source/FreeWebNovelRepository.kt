package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
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
			"$baseUrl/search?keyword=${URLEncoder.encode(query, "UTF-8")}&page=$page"
		} else {
			val sortPath = when (order) {
				SortOrder.NEWEST -> "latest-novel"
				SortOrder.UPDATED -> "latest-release"
				else -> "most-popular"
			}
			"$baseUrl/sort/$sortPath/$page"
		}

		val doc = fetchDocument(url)
		val items = doc.select("div.li-row")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst("div.tit a") ?: return@mapNotNull null
			val title = titleEl.attr("title").ifEmpty { titleEl.text().trim() }
			val href = titleEl.absUrl("href").ifEmpty { "$baseUrl${titleEl.attr("href")}" }
			val coverEl = element.selectFirst("div.pic img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()
			val desc = element.selectFirst("div.desc")?.text()?.trim()

			Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitle = null,
				url = href,
				publicUrl = href,
				rating = 0f,
				isNsfw = false,
				coverUrl = coverUrl,
				tags = emptySet<org.koitharu.kotatsu.parsers.model.MangaTag>(),
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
		val desc = doc.selectFirst("div.inner")?.text()?.trim() ?: manga.description
		val cover = doc.selectFirst("div.pic img")?.absUrl("src") ?: manga.coverUrl
		val author = doc.selectFirst("div.author a, span.author")?.text()?.trim()

		val chapterElements = doc.select("ul.ul-list5 li a")
		val chapters = chapterElements.mapIndexed { index, a ->
			val chapTitle = a.attr("title").ifEmpty { a.text().trim() }
			val chapUrl = a.absUrl("href").ifEmpty { "$baseUrl${a.attr("href")}" }
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
		val article = doc.selectFirst("div#article") ?: doc.body()
		// Remove ads, scripts, and navigation
		article.select(".ad-box, script, style, iframe, .m-page, .m-nav, a#prev_url, a#next_url").remove()
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
}
