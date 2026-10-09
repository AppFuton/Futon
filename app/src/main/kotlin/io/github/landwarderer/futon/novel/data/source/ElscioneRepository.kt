package io.github.landwarderer.futon.novel.data.source

import android.content.Context
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.novel.data.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
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
	private val rootPrefix = "/LNWNCentral%20Dump/"
	private val directoryPath = "$baseUrl$rootPrefix"

	private val cacheMutex = Mutex()
	private var cachedNovels: List<Manga>? = null

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val allNovels = getOrFetchAllNovels()
		val query = filter?.query?.trim()?.lowercase().orEmpty()

		var filtered = if (query.isNotEmpty()) {
			allNovels.filter { it.title.lowercase().contains(query) }
		} else {
			allNovels
		}

		when (order) {
			SortOrder.ALPHABETICAL -> {
				filtered = filtered.sortedBy { it.title.lowercase() }
			}
			SortOrder.RELEVANCE -> {
				if (query.isNotEmpty()) {
					filtered = filtered.sortedWith(
						compareBy(
							{ !it.title.lowercase().startsWith(query) },
							{ it.title.lowercase().indexOf(query) },
							{ it.title.length },
						)
					)
				}
			}
			else -> Unit
		}

		return filtered.drop(offset).take(30)
	}

	private suspend fun getOrFetchAllNovels(): List<Manga> {
		cachedNovels?.let { return it }
		return cacheMutex.withLock {
			cachedNovels?.let { return@withLock it }

			val payload = JSONObject().apply {
				put("action", "get")
				put("items", JSONObject().apply {
					put("href", rootPrefix)
					put("what", 1)
				})
			}.toString()

			val responseJsonStr = postJson("$directoryPath?", payload)
			val rootObj = JSONObject(responseJsonStr)
			val itemsArray = rootObj.optJSONArray("items") ?: return@withLock emptyList()

			val novels = mutableListOf<Manga>()
			for (i in 0 until itemsArray.length()) {
				val item = itemsArray.getJSONObject(i)
				val href = item.optString("href")

				// Direct child folders of /LNWNCentral%20Dump/
				if (!href.startsWith(rootPrefix) || href == rootPrefix || !href.endsWith("/")) {
					continue
				}

				val relPath = href.removePrefix(rootPrefix).removeSuffix("/")
				if (relPath.contains("/")) {
					continue
				}

				val decodedTitle = try {
					URLDecoder.decode(relPath, "UTF-8")
				} catch (_: Exception) {
					relPath
				}

				val fullUrl = "$baseUrl$href"
				novels.add(
					Manga(
						id = "${source.name}_$fullUrl".longHashCode(),
						title = decodedTitle,
						altTitle = null,
						url = fullUrl,
						publicUrl = fullUrl,
						rating = 0f,
						isNsfw = false,
						coverUrl = "",
						tags = emptySet(),
						state = null,
						author = null,
						largeCoverUrl = null,
						description = null,
						chapters = null,
						source = source,
					)
				)
			}

			cachedNovels = novels
			novels
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val relativeHref = if (manga.url.startsWith(baseUrl)) {
			manga.url.removePrefix(baseUrl)
		} else {
			manga.url
		}

		if (!relativeHref.endsWith("/")) {
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

		val payload = JSONObject().apply {
			put("action", "get")
			put("items", JSONObject().apply {
				put("href", relativeHref)
				put("what", 2)
			})
		}.toString()

		val responseJsonStr = postJson("$directoryPath?", payload)
		val rootObj = JSONObject(responseJsonStr)
		val itemsArray = rootObj.optJSONArray("items") ?: return manga.copy(chapters = emptyList())

		// Collect all files in this subtree
		data class FileItem(val href: String, val time: Long, val size: Long)
		val files = mutableListOf<FileItem>()

		for (i in 0 until itemsArray.length()) {
			val item = itemsArray.getJSONObject(i)
			val href = item.optString("href")
			if (href.startsWith(relativeHref) && !href.endsWith("/")) {
				val lower = href.lowercase()
				if (lower.endsWith(".epub") || lower.endsWith(".pdf")) {
					files.add(
						FileItem(
							href = href,
							time = item.optLong("time", 0L),
							size = item.optLong("size", 0L),
						)
					)
				}
			}
		}

		// Group files by parent folder
		val byFolder = files.groupBy {
			it.href.substring(0, it.href.lastIndexOf('/') + 1)
		}

		val selectedFiles = mutableListOf<FileItem>()
		for ((_, folderFiles) in byFolder) {
			val hasEpub = folderFiles.any { it.href.endsWith(".epub", ignoreCase = true) }
			if (hasEpub) {
				selectedFiles.addAll(folderFiles.filter { it.href.endsWith(".epub", ignoreCase = true) })
			} else {
				selectedFiles.addAll(folderFiles.filter { it.href.endsWith(".pdf", ignoreCase = true) })
			}
		}

		// Sort naturally by path/name
		selectedFiles.sortBy { it.href }

		val chapters = selectedFiles.mapIndexed { index, fileItem ->
			val rawName = try {
				URLDecoder.decode(fileItem.href.substringAfterLast('/'), "UTF-8")
			} catch (_: Exception) {
				fileItem.href.substringAfterLast('/')
			}
			val cleanTitle = rawName.removeSuffix(".epub").removeSuffix(".EPUB")
				.removeSuffix(".pdf").removeSuffix(".PDF")
			val fullUrl = "$baseUrl${fileItem.href}"

			MangaChapter(
				id = "${source.name}_$fullUrl".longHashCode(),
				title = cleanTitle,
				number = (index + 1).toFloat(),
				volume = 0,
				url = fullUrl,
				uploadDate = fileItem.time,
				source = source,
				scanlator = null,
				branch = null,
			)
		}

		return manga.copy(chapters = chapters)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val cacheDir = File(context.cacheDir, "novel_cache").apply { mkdirs() }
		val isPdf = chapter.url.endsWith(".pdf", ignoreCase = true)
		val ext = if (isPdf) "pdf" else "epub"
		val cachedFile = File(cacheDir, "${chapter.url.longHashCode()}.$ext")

		if (!cachedFile.exists() || cachedFile.length() == 0L) {
			val request = Request.Builder()
				.url(chapter.url)
				.tag(MangaSource::class.java, source)
				.header("User-Agent", DEFAULT_USER_AGENT)
				.header("Referer", directoryPath)
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					throw RuntimeException("HTTP ${response.code} downloading novel file")
				}
				response.body?.byteStream()?.use { input ->
					FileOutputStream(cachedFile).use { output ->
						input.copyTo(output)
					}
				}
			}
		}

		val htmlContent = if (isPdf) {
			"<div style='padding: 24px; text-align: center;'><p>PDF file downloaded: ${cachedFile.name}</p></div>"
		} else {
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
			sb.toString().ifEmpty {
				"<div style='padding: 24px; text-align: center;'><p>Unable to read EPUB contents.</p></div>"
			}
		}

		listOf(
			MangaPage(
				id = chapter.id,
				url = htmlContent,
				preview = null,
				source = source,
			)
		)
	}
}
