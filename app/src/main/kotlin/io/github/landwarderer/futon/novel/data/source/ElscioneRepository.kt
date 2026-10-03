package io.github.landwarderer.futon.novel.data.source

import android.content.Context
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.novel.data.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder

class ElscioneRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
	private val context: Context,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.ELSCIONE) {

	private val baseUrl = source.defaultUrl
	private val directoryPath = "$baseUrl/Officially%20Translated%20Light%20Novels/"

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val doc = fetchDocument(directoryPath)
		val query = filter?.query?.trim()?.lowercase().orEmpty()

		val links = doc.select("a[href]")
		val novels = mutableListOf<Manga>()

		for (a in links) {
			val href = a.attr("href")
			if (href.startsWith("?") || href.startsWith("/") || href.startsWith("..") || href.equals("../", ignoreCase = true)) {
				continue
			}

			val decodedName = try {
				URLDecoder.decode(href.removeSuffix("/"), "UTF-8")
			} catch (_: Exception) {
				href.removeSuffix("/")
			}

			if (query.isNotEmpty() && !decodedName.lowercase().contains(query)) {
				continue
			}

			val fullUrl = a.absUrl("href").ifEmpty { "$directoryPath$href" }

			novels.add(
				Manga(
					id = "${source.name}_$fullUrl".longHashCode(),
					title = decodedName,
					altTitle = null,
					url = fullUrl,
					publicUrl = fullUrl,
					rating = 0f,
					isNsfw = false,
					coverUrl = "",
					tags = emptySet<org.koitharu.kotatsu.parsers.model.MangaTag>(),
					state = null,
					author = null,
					largeCoverUrl = null,
					description = null,
					chapters = null,
					source = source,
				)
			)
		}

		return novels.drop(offset).take(30)
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		if (!manga.url.endsWith("/")) {
			// Direct file
			val chapter = MangaChapter(
				id = "${source.name}_${manga.url}".longHashCode(),
				title = manga.title,
				number = 1f,
				volume = 0,
				url = manga.url,
				uploadDate = 0L,
				source = source,
				scanlator = null,
				branch = null,
			)
			return manga.copy(chapters = listOf(chapter))
		}

		val doc = fetchDocument(manga.url)
		val links = doc.select("a[href]")
		val chapters = mutableListOf<MangaChapter>()

		var index = 0
		for (a in links) {
			val href = a.attr("href")
			if (href.startsWith("?") || href.startsWith("/") || href.startsWith("..")) continue
			if (!href.endsWith(".epub", ignoreCase = true) && !href.endsWith(".pdf", ignoreCase = true)) continue

			val decodedName = try {
				URLDecoder.decode(href, "UTF-8").removeSuffix(".epub").removeSuffix(".pdf")
			} catch (_: Exception) {
				href
			}

			val fullUrl = a.absUrl("href").ifEmpty { "${manga.url}$href" }
			chapters.add(
				MangaChapter(
					id = "${source.name}_$fullUrl".longHashCode(),
					title = decodedName,
					number = (++index).toFloat(),
					volume = 0,
					url = fullUrl,
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				)
			)
		}

		return manga.copy(chapters = chapters)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val cacheDir = File(context.cacheDir, "novel_cache").apply { mkdirs() }
		val cachedFile = File(cacheDir, "${chapter.url.longHashCode()}.epub")

		if (!cachedFile.exists() || cachedFile.length() == 0L) {
			val request = Request.Builder()
				.url(chapter.url)
				.header("User-Agent", DEFAULT_USER_AGENT)
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					throw RuntimeException("HTTP ${response.code} downloading epub")
				}
				response.body?.byteStream()?.use { input ->
					FileOutputStream(cachedFile).use { output ->
						input.copyTo(output)
					}
				}
			}
		}

		val parser = EpubParser(cachedFile)
		val book = parser.parseBook()
		val sb = StringBuilder()
		if (book != null) {
			for (chap in book.chapters) {
				val chapHtml = parser.getChapterHtml(chap.href)
				if (chapHtml != null) {
					sb.append("<div class='epub-chapter-section'>")
					sb.append("<h2>${chap.title}</h2>")
					sb.append(chapHtml)
					sb.append("</div><hr/>")
				}
			}
		}

		listOf(
			MangaPage(
				id = chapter.id,
				url = sb.toString(),
				preview = null,
				source = source,
			)
		)
	}
}
