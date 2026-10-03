package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode

class LnoriRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.LNORI) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			"$baseUrl/books?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page"
		} else {
			"$baseUrl/books?page=$page"
		}

		val doc = fetchDocument(url)
		val items = doc.select("a[href*='/book/'], a[href*='/series/']")
		val seen = mutableSetOf<String>()

		return items.mapNotNull { a ->
			val href = a.absUrl("href")
			if (!href.contains("/book/") && !href.contains("/series/")) return@mapNotNull null
			if (!seen.add(href)) return@mapNotNull null

			val title = a.selectFirst("h3, h2, .title")?.text()?.trim()
				?: a.text().trim().takeIf { it.isNotBlank() && it.length > 2 }
				?: return@mapNotNull null

			val coverEl = a.selectFirst("img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()

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
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val title = doc.selectFirst("h1")?.text()?.trim() ?: manga.title
		val cover = doc.selectFirst("img[src*='cover'], .cover img, img")?.absUrl("src") ?: manga.coverUrl
		val desc = doc.selectFirst(".synopsis, .description, p.summary")?.text()?.trim() ?: manga.description

		val headings = doc.select("h2.chapter-title, h2")
		val chapters = if (headings.isNotEmpty()) {
			headings.mapIndexed { index, h2 ->
				val chapTitle = h2.text().trim().ifEmpty { "Chapter ${index + 1}" }
				val chapUrl = "${manga.url}#chapter-$index"
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
		} else {
			listOf(
				MangaChapter(
					id = "${source.name}_${manga.url}".longHashCode(),
					title = "Complete Volume",
					number = 1f,
					volume = 0,
					url = manga.url,
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				)
			)
		}

		return manga.copy(
			title = title,
			description = desc,
			coverUrl = cover,
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val rawUrl = chapter.url.substringBefore('#')
		val fragment = chapter.url.substringAfter('#', "")
		val doc = fetchDocument(rawUrl)

		// Fix relative image links to absolute
		for (img in doc.select("img")) {
			val src = img.attr("src")
			if (src.startsWith("/")) {
				img.attr("src", "$baseUrl$src")
			}
		}

		val contentHtml = if (fragment.startsWith("chapter-")) {
			val index = fragment.removePrefix("chapter-").toIntOrNull() ?: 0
			val headings = doc.select("h2.chapter-title, h2")
			if (index in headings.indices) {
				val startH2 = headings[index]
				val nextH2 = headings.getOrNull(index + 1)
				val sb = java.lang.StringBuilder()
				sb.append(startH2.outerHtml())

				var sibling = startH2.nextElementSibling()
				while (sibling != null && sibling != nextH2) {
					sb.append(sibling.outerHtml())
					sibling = sibling.nextElementSibling()
				}
				sb.toString()
			} else {
				doc.body().html()
			}
		} else {
			val container = doc.selectFirst(".reader-content, article, main") ?: doc.body()
			container.select("script, style, nav, header, footer").remove()
			container.html()
		}

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
