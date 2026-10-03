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

class RanobesRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.RANOBES) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			"$baseUrl/index.php?do=search&subaction=search&story=${URLEncoder.encode(query, "UTF-8")}"
		} else {
			val sortPath = when (order) {
				SortOrder.UPDATED -> "updates"
				SortOrder.POPULARITY -> "ranking"
				else -> "novels"
			}
			"$baseUrl/$sortPath/page/$page/"
		}

		val doc = fetchDocument(url)
		val items = doc.select(".short-story, article.story")
		return items.mapNotNull { element ->
			val titleEl = element.selectFirst(".title a, h2 a") ?: return@mapNotNull null
			val title = titleEl.text().trim()
			val href = titleEl.absUrl("href")

			val coverEl = element.selectFirst(".poster img, img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()
			val desc = element.selectFirst(".story, .description")?.text()?.trim()

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
		val cover = doc.selectFirst(".poster img, .show-poster img")?.absUrl("src") ?: manga.coverUrl
		val desc = doc.selectFirst(".story, #fs-info, .moreless__full")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst("a[href*='/translater/'], a[href*='/author/']")?.text()?.trim()

		val chapterLinks = doc.select("#chapters-list a, .chapters-scroll a, .chapters-list a, a.chapter-item")
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
		val content = doc.selectFirst("#arrticle, #article, .entry-content") ?: doc.body()
		content.select("script, style, iframe, .ads, .share").remove()
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
