package io.github.landwarderer.futon.novel.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

class WuxiaworldRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.WUXIAWORLD) {

	private val baseUrl = source.defaultUrl

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val url = if (query.isNotEmpty()) {
			"$baseUrl/novels?search=${URLEncoder.encode(query, "UTF-8")}&page=$page"
		} else {
			"$baseUrl/novels?page=$page"
		}

		val doc = fetchDocument(url)
		val items = doc.select("a[href*='/novel/']")
		val seen = mutableSetOf<String>()

		return items.mapNotNull { a ->
			val href = a.absUrl("href")
			if (!href.contains("/novel/") || href.split("/novel/").getOrNull(1)?.contains('/') == true) return@mapNotNull null
			if (!seen.add(href)) return@mapNotNull null

			val title = a.selectFirst("p.font-bold, .title, h5, h4")?.text()?.trim()
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
		val title = doc.selectFirst("h1, h2.font-bold")?.text()?.trim() ?: manga.title
		val cover = doc.selectFirst("img.cover, img[src*='covers']")?.absUrl("src") ?: manga.coverUrl
		val desc = doc.selectFirst(".synopsis, .description, div[class*='synopsis']")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst("span[class*='author'], div[class*='author']")?.text()?.trim()

		val slug = manga.url.substringAfterLast("/novel/").trimEnd('/')
		val html = doc.html()
		val novelId = extractNovelId(html, slug)

		val chapters = if (novelId != null) {
			fetchChaptersViaGrpc(novelId, slug)
		} else {
			emptyList()
		}.ifEmpty {
			parseChapterLinksFromDoc(doc)
		}

		return manga.copy(
			title = title,
			description = desc,
			coverUrl = cover,
			authors = author?.let { setOf(it) } ?: manga.authors,
			chapters = chapters,
		)
	}

	private fun extractNovelId(html: String, slug: String): Int? {
		val patterns = listOf(
			Regex(""""id":\s*(\d+)[^}]*"slug":\s*"$slug""""),
			Regex(""""slug":\s*"$slug"[^}]*"id":\s*(\d+)""""),
			Regex(""""novelId":\s*(\d+)"""),
			Regex(""""id":\s*(\d+),\s*"name":""""),
		)
		for (pattern in patterns) {
			val match = pattern.find(html)
			if (match != null) {
				val id = match.groupValues[1].toIntOrNull()
				if (id != null && id > 0) return id
			}
		}
		return null
	}

	private suspend fun fetchChaptersViaGrpc(novelId: Int, novelSlug: String): List<MangaChapter> = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val varintBuf = ByteArrayOutputStream()
			var v = novelId
			while ((v and 0x7F.inv()) != 0) {
				varintBuf.write((v and 0x7F) or 0x80)
				v = v ushr 7
			}
			varintBuf.write(v and 0x7F)
			val varintBytes = varintBuf.toByteArray()

			val payload = byteArrayOf(0x08.toByte()) + varintBytes
			val len = payload.size
			val frame = ByteArray(5 + len)
			frame[0] = 0x00 // Data frame
			frame[1] = (len ushr 24).toByte()
			frame[2] = (len ushr 16).toByte()
			frame[3] = (len ushr 8).toByte()
			frame[4] = len.toByte()
			System.arraycopy(payload, 0, frame, 5, len)

			val request = Request.Builder()
				.url("https://api2.wuxiaworld.com/wuxiaworld.api.v2.Chapters/GetChapterList")
				.header("User-Agent", DEFAULT_USER_AGENT)
				.header("Content-Type", "application/grpc-web+proto")
				.header("x-grpc-web", "1")
				.post(frame.toRequestBody("application/grpc-web+proto".toMediaType()))
				.build()

			val responseBytes = httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) return@runCatchingCancellable emptyList()
				response.body?.bytes()
			} ?: return@runCatchingCancellable emptyList()

			parseChaptersProtobuf(responseBytes, novelSlug)
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrDefault(emptyList())
	}

	private fun parseChaptersProtobuf(responseBytes: ByteArray, novelSlug: String): List<MangaChapter> {
		val chapters = mutableListOf<MangaChapter>()
		var framePos = 0
		while (framePos + 5 <= responseBytes.size) {
			val flag = responseBytes[framePos].toInt() and 0xFF
			val msgLen = ((responseBytes[framePos + 1].toInt() and 0xFF) shl 24) or
				((responseBytes[framePos + 2].toInt() and 0xFF) shl 16) or
				((responseBytes[framePos + 3].toInt() and 0xFF) shl 8) or
				(responseBytes[framePos + 4].toInt() and 0xFF)
			framePos += 5
			if (framePos + msgLen > responseBytes.size) break
			if (flag == 0) {
				val reader = ProtoReader(responseBytes, framePos, framePos + msgLen)
				while (reader.hasRemaining()) {
					val tag = reader.readVarint()
					val fieldNum = (tag ushr 3).toInt()
					val wireType = (tag and 0x07).toInt()
					when (wireType) {
						0 -> reader.readVarint()
						1 -> reader.skip(8)
						2 -> {
							val length = reader.readVarint().toInt()
							if (fieldNum == 1) { // items (ChapterGroupItem)
								val groupReader = reader.subReader(length)
								while (groupReader.hasRemaining()) {
									val gTag = groupReader.readVarint()
									val gField = (gTag ushr 3).toInt()
									val gWire = (gTag and 0x07).toInt()
									when (gWire) {
										0 -> groupReader.readVarint()
										1 -> groupReader.skip(8)
										2 -> {
											val gLen = groupReader.readVarint().toInt()
											if (gField == 6) { // chapterList (ChapterItem)
												val cReader = groupReader.subReader(gLen)
												var cName = ""
												var cSlug = ""
												while (cReader.hasRemaining()) {
													val cTag = cReader.readVarint()
													val cField = (cTag ushr 3).toInt()
													val cWire = (cTag and 0x07).toInt()
													when (cWire) {
														0 -> cReader.readVarint()
														1 -> cReader.skip(8)
														2 -> {
															val cLen = cReader.readVarint().toInt()
															when (cField) {
																2 -> cName = cReader.readString(cLen)
																3 -> cSlug = cReader.readString(cLen)
																else -> cReader.skip(cLen)
															}
														}
														5 -> cReader.skip(4)
														else -> break
													}
												}
												if (cSlug.isNotEmpty()) {
													val chapUrl = "$baseUrl/novel/$novelSlug/$cSlug"
													val chapTitle = cName.ifEmpty { "Chapter ${chapters.size + 1}" }
													chapters.add(
														MangaChapter(
															id = "${source.name}_$chapUrl".longHashCode(),
															title = chapTitle,
															number = (chapters.size + 1).toFloat(),
															volume = 0,
															url = chapUrl,
															uploadDate = 0L,
															source = source,
															scanlator = null,
															branch = null,
														),
													)
												}
											} else {
												groupReader.skip(gLen)
											}
										}
										5 -> groupReader.skip(4)
										else -> break
									}
								}
							} else {
								reader.skip(length)
							}
						}
						5 -> reader.skip(4)
						else -> break
					}
				}
			}
			framePos += msgLen
		}
		return chapters
	}

	private fun parseChapterLinksFromDoc(doc: org.jsoup.nodes.Document): List<MangaChapter> {
		val chapterLinks = doc.select("a[href*='/chapter/'], a[href*='chapter-'], a[href*='snd-']")
		val seenUrls = mutableSetOf<String>()
		return chapterLinks.mapNotNull { a ->
			val chapUrl = a.absUrl("href")
			if (!chapUrl.contains("/novel/") || !seenUrls.add(chapUrl)) return@mapNotNull null
			val chapTitle = a.text().trim().ifEmpty { "Chapter" }
			chapTitle to chapUrl
		}.mapIndexed { index, (chapTitle, chapUrl) ->
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

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(chapter.url)
		val content = doc.selectFirst(".chapter-body, .fr-view, .chapter-content, #chapter-content") ?: doc.body()
		content.select("script, style, iframe, button, .chapter-nav, .ad-box").remove()
		val contentHtml = content.html()

		return listOf(
			MangaPage(
				id = chapter.id,
				url = contentHtml,
				preview = null,
				source = source,
			),
		)
	}

	private class ProtoReader(
		private val bytes: ByteArray,
		private var pos: Int = 0,
		private val limit: Int = bytes.size,
	) {
		fun hasRemaining(): Boolean = pos < limit

		fun readVarint(): Long {
			var result = 0L
			var shift = 0
			while (pos < limit) {
				val b = bytes[pos++].toInt()
				result = result or ((b and 0x7F).toLong() shl shift)
				if ((b and 0x80) == 0) break
				shift += 7
			}
			return result
		}

		fun readString(length: Int): String {
			val end = (pos + length).coerceAtMost(limit)
			val str = String(bytes, pos, end - pos, Charsets.UTF_8)
			pos = end
			return str
		}

		fun skip(length: Int) {
			pos = (pos + length).coerceAtMost(limit)
		}

		fun subReader(length: Int): ProtoReader {
			val end = (pos + length).coerceAtMost(limit)
			val sub = ProtoReader(bytes, pos, end)
			pos = end
			return sub
		}
	}
}

