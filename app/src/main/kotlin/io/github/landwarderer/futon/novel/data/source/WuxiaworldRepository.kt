package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

class WuxiaworldRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.WUXIAWORLD) {

	private val baseUrl = source.defaultUrl

	private val catalogMutex = Mutex()
	private var cachedSortKey: String? = null
	private var cachedCatalog: List<Manga>? = null

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val query = filter?.query?.trim().orEmpty()
		return runCatchingCancellable {
			fetchNovelsViaGrpc(offset, order, query)
		}.getOrElse {
			it.printStackTraceDebug()
			fetchNovelsViaHtml(offset, order, query)
		}
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val slug = manga.url.substringAfterLast("/novel/").trimEnd('/').substringBefore('/')
		val doc = fetchDocument(manga.url)
		val html = doc.html()

		// 1. Try React Query state embedded in static SSR
		val reactQueryJson = extractReactQueryJson(html)
		val itemJson = findNovelItemInReactQuery(reactQueryJson, slug)
		val jsonManga = itemJson?.let { parseNovelItemFromJson(it) }
		val novelIdFromJson = itemJson?.optInt("id", -1)?.takeIf { it > 0 }

		// 2. Fallback to gRPC GetNovel if JSON is missing
		val grpcManga = if (jsonManga == null) fetchNovelViaGrpc(slug) else null

		// 3. Fallback to DOM elements
		val title = jsonManga?.title?.ifBlank { null }
			?: grpcManga?.title?.ifBlank { null }
			?: doc.selectFirst("h1, h2.font-bold")?.text()?.trim()?.takeIf { !it.equals("Ongoing", ignoreCase = true) }
			?: manga.title.takeIf { !it.equals("Ongoing", ignoreCase = true) }
			?: slug

		val cover = jsonManga?.coverUrl?.ifBlank { null }
			?: grpcManga?.coverUrl?.ifBlank { null }
			?: doc.selectFirst("img[src*='covers'], img.cover")?.absUrl("src")
			?: manga.coverUrl

		val desc = jsonManga?.description
			?: grpcManga?.description
			?: doc.selectFirst(".synopsis, .description, div[class*='synopsis']")?.text()?.trim()
			?: manga.description

		val authors = jsonManga?.authors?.takeIf { it.isNotEmpty() }
			?: grpcManga?.authors?.takeIf { it.isNotEmpty() }
			?: doc.selectFirst("span[class*='author'], div[class*='author']")?.text()?.trim()?.let { setOf(it) }
			?: manga.authors

		val state = jsonManga?.state ?: grpcManga?.state ?: manga.state
		val tags = jsonManga?.tags?.takeIf { it.isNotEmpty() }
			?: grpcManga?.tags?.takeIf { it.isNotEmpty() }
			?: manga.tags
		val rating = when {
			jsonManga != null && jsonManga.rating > 0f -> jsonManga.rating
			grpcManga != null && grpcManga.rating > 0f -> grpcManga.rating
			else -> manga.rating
		}

		val novelId = novelIdFromJson ?: extractNovelId(html, slug)

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
			largeCoverUrl = cover,
			authors = authors,
			state = state,
			tags = tags,
			rating = rating,
			chapters = chapters,
		)
	}

	private suspend fun fetchNovelsViaGrpc(offset: Int, order: SortOrder?, query: String): List<Manga> {
		if (query.isNotEmpty()) {
			val payload = buildSearchNovelsRequest(
				query = query,
				sortType = 1,
				sortDirection = 1,
				count = 30,
			)
			val responseBytes = callGrpc("wuxiaworld.api.v2.Novels/SearchNovels", payload)
				?: return emptyList()
			val (novels, _) = parseSearchNovelsWithLastId(responseBytes)
			return novels.drop(offset).take(30)
		}

		val sortType = when (order) {
			SortOrder.POPULARITY -> 1
			SortOrder.NEWEST -> 2
			SortOrder.UPDATED -> 7
			SortOrder.ALPHABETICAL -> 4
			SortOrder.RATING -> 6
			else -> 1
		}
		val sortDirection = if (order == SortOrder.ALPHABETICAL) 0 else 1

		val allNovels = getOrFetchAllNovels(sortType, sortDirection)
		return allNovels.drop(offset).take(30)
	}

	private suspend fun getOrFetchAllNovels(sortType: Int, sortDirection: Int): List<Manga> {
		val key = "$sortType-$sortDirection"
		if (cachedSortKey == key && cachedCatalog != null) {
			return cachedCatalog!!
		}
		return catalogMutex.withLock {
			if (cachedSortKey == key && cachedCatalog != null) {
				return@withLock cachedCatalog!!
			}

			val p1 = buildSearchNovelsRequest(
				query = null,
				sortType = sortType,
				sortDirection = sortDirection,
				count = 100,
			)
			val resp1 = callGrpc("wuxiaworld.api.v2.Novels/SearchNovels", p1)
			val novels = mutableListOf<Manga>()
			var lastId: Int? = null

			if (resp1 != null) {
				val (batch1, id1) = parseSearchNovelsWithLastId(resp1)
				novels.addAll(batch1)
				lastId = id1
			}

			// If there are more items (full catalog is ~164 novels), fetch remaining
			if (novels.size >= 100 && lastId != null) {
				val p2 = buildSearchNovelsRequest(
					query = null,
					sortType = sortType,
					sortDirection = sortDirection,
					count = 100,
					searchAfterId = lastId,
				)
				val resp2 = callGrpc("wuxiaworld.api.v2.Novels/SearchNovels", p2)
				if (resp2 != null) {
					val (batch2, _) = parseSearchNovelsWithLastId(resp2)
					novels.addAll(batch2)
				}
			}

			if (novels.isNotEmpty()) {
				cachedSortKey = key
				cachedCatalog = novels
			}
			novels
		}
	}

	private suspend fun fetchNovelsViaHtml(offset: Int, order: SortOrder?, query: String): List<Manga> {
		val page = (offset / 20) + 1
		val url = if (query.isNotEmpty()) {
			"$baseUrl/novels?search=${URLEncoder.encode(query, "UTF-8")}&page=$page"
		} else {
			"$baseUrl/novels?page=$page"
		}

		val doc = fetchDocument(url)
		val reactQuery = extractReactQueryJson(doc.html())
		if (reactQuery != null) {
			val novels = parseNovelsFromReactQuery(reactQuery)
			if (novels.isNotEmpty()) {
				val filtered = if (query.isNotEmpty()) {
					novels.filter { it.title.contains(query, ignoreCase = true) }
				} else {
					novels
				}
				return filtered.drop(offset % novels.size).take(20)
			}
		}

		// DOM fallback
		val seen = mutableSetOf<String>()
		val cards = doc.select(".grid > div, div[class*='grid-cols'] > div, div[class*='gap']")
		val result = mutableListOf<Manga>()

		for (card in cards) {
			val links = card.select("a[href*='/novel/']")
			if (links.isEmpty()) continue
			val firstLink = links.firstOrNull() ?: continue
			val href = firstLink.absUrl("href")
			if (!href.contains("/novel/") || href.split("/novel/").getOrNull(1)?.contains('/') == true) continue
			if (!seen.add(href)) continue

			val title = card.selectFirst("a strong, strong, h3, h4, h5, p.font-bold, .title")?.text()?.trim()
				?.takeIf { !it.equals("Ongoing", ignoreCase = true) && !it.equals("Completed", ignoreCase = true) }
				?: card.selectFirst("img[alt]")?.attr("alt")?.trim()
					?.takeIf { it.isNotBlank() && !it.equals("Ongoing", ignoreCase = true) }
				?: links.map { it.text().trim() }.firstOrNull {
					it.isNotBlank() && !it.equals("Ongoing", ignoreCase = true) && !it.equals("Completed", ignoreCase = true)
				}
				?: continue

			val coverEl = card.selectFirst("img[src*='covers'], img")
			val coverUrl = coverEl?.absUrl("src") ?: coverEl?.attr("src").orEmpty()
			val cardText = card.text()
			val state = when {
				cardText.contains("Ongoing", ignoreCase = true) -> MangaState.ONGOING
				cardText.contains("Completed", ignoreCase = true) -> MangaState.FINISHED
				else -> null
			}

			result.add(
				Manga(
					id = "${source.name}_$href".longHashCode(),
					title = title,
					altTitle = null,
					url = href,
					publicUrl = href,
					rating = 0f,
					isNsfw = false,
					coverUrl = coverUrl,
					tags = emptySet(),
					state = state,
					author = null,
					largeCoverUrl = coverUrl,
					description = null,
					chapters = null,
					source = source,
				)
			)
		}
		return result
	}

	private suspend fun fetchNovelViaGrpc(slug: String): Manga? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val payload = buildGetNovelRequest(slug)
			val responseBytes = callGrpc("wuxiaworld.api.v2.Novels/GetNovel", payload)
				?: return@runCatchingCancellable null
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
						if (wireType == 2) {
							val length = reader.readVarint().toInt()
							if (fieldNum == 1) { // NovelItem
								val sub = reader.subReader(length)
								val (manga, _) = parseNovelItemFromProto(sub)
								if (manga != null) return@runCatchingCancellable manga
							} else {
								reader.skip(length)
							}
						} else {
							reader.skipByWire(wireType)
						}
					}
				}
				framePos += msgLen
			}
			null
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	private fun extractReactQueryJson(html: String): JSONObject? {
		val marker = "window.__REACT_QUERY_STATE__ ="
		val idx = html.indexOf(marker)
		if (idx == -1) return null
		val braceStart = html.indexOf('{', idx + marker.length)
		if (braceStart == -1) return null
		var end = html.indexOf("};\n", braceStart)
		if (end == -1) {
			end = html.indexOf("};", braceStart)
		}
		if (end == -1) return null
		val jsonString = html.substring(braceStart, end + 1)
		return runCatching { JSONObject(jsonString) }.getOrNull()
	}

	private fun findNovelItemInReactQuery(json: JSONObject?, slug: String): JSONObject? {
		val queries = json?.optJSONArray("queries") ?: return null
		for (i in 0 until queries.length()) {
			val q = queries.optJSONObject(i) ?: continue
			val item = q.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("item")
			if (item != null) {
				val itemSlug = item.optString("slug").ifBlank { item.optStringValue("slug") }
				if (itemSlug == null || itemSlug.equals(slug, ignoreCase = true)) {
					return item
				}
			}
		}
		return null
	}

	private fun parseNovelsFromReactQuery(json: JSONObject?): List<Manga> {
		val queries = json?.optJSONArray("queries") ?: return emptyList()
		val list = mutableListOf<Manga>()
		for (i in 0 until queries.length()) {
			val q = queries.optJSONObject(i) ?: continue
			val queryKey = q.optJSONArray("queryKey")?.optString(0)
			if (queryKey != "novels") continue
			val pages = q.optJSONObject("state")?.optJSONObject("data")?.optJSONArray("pages")
			if (pages != null) {
				for (p in 0 until pages.length()) {
					val items = pages.optJSONObject(p)?.optJSONArray("items") ?: continue
					for (j in 0 until items.length()) {
						val item = items.optJSONObject(j) ?: continue
						parseNovelItemFromJson(item)?.let { list.add(it) }
					}
				}
			}
		}
		return list
	}

	private fun parseNovelItemFromJson(item: JSONObject): Manga? {
		val name = item.optStringValue("name")?.trim().orEmpty()
		val slug = item.optString("slug").trim().ifEmpty {
			item.optStringValue("slug")?.trim().orEmpty()
		}
		if (name.isEmpty() && slug.isEmpty()) return null

		val novelUrl = "$baseUrl/novel/$slug"
		val cover = item.optStringValue("coverUrl")?.trim().orEmpty()
		val author = item.optStringValue("authorName")?.trim()

		val statusInt = item.optInt("status", -1)
		val state = when (statusInt) {
			0 -> MangaState.FINISHED
			1 -> MangaState.ONGOING
			2 -> MangaState.PAUSED
			else -> null
		}

		val tags = parseTagsFromJson(item)
		val rating = parseRatingFromJson(item)

		val synopsisRaw = item.optStringValue("synopsis") ?: item.optStringValue("description")
		val desc = synopsisRaw?.let { Jsoup.parse(it).text().trim().ifBlank { null } }

		return Manga(
			id = "${source.name}_$novelUrl".longHashCode(),
			title = name.ifBlank { slug },
			altTitle = null,
			url = novelUrl,
			publicUrl = novelUrl,
			rating = rating,
			isNsfw = false,
			coverUrl = cover,
			tags = tags,
			state = state,
			author = author,
			largeCoverUrl = cover,
			description = desc,
			chapters = null,
			source = source,
		)
	}

	private fun parseTagsFromJson(item: JSONObject): Set<MangaTag> {
		val tagsSet = mutableSetOf<MangaTag>()
		val genresArray = item.optJSONArray("genres")
		if (genresArray != null) {
			for (i in 0 until genresArray.length()) {
				val g = genresArray.optString(i).trim()
				if (g.isNotEmpty()) {
					tagsSet.add(MangaTag(title = g, key = g, source = source))
				}
			}
		}
		val tagsArray = item.optJSONArray("tags")
		if (tagsArray != null) {
			for (i in 0 until tagsArray.length()) {
				val t = tagsArray.optString(i).trim()
				if (t.isNotEmpty()) {
					tagsSet.add(MangaTag(title = t, key = t, source = source))
				}
			}
		}
		return tagsSet
	}

	private fun parseRatingFromJson(item: JSONObject): Float {
		val reviewObj = item.optJSONObject("reviewInfo")
		val ratingObj = reviewObj?.optJSONObject("rating")
		val ratingVal = ratingObj?.optDouble("value") ?: reviewObj?.optDouble("rating")
		return if (ratingVal != null && !ratingVal.isNaN() && ratingVal > 0.0) {
			ratingVal.toFloat().coerceIn(0f, 1f)
		} else {
			0f
		}
	}

	private fun JSONObject.optStringValue(key: String): String? {
		val obj = opt(key) ?: return null
		return when (obj) {
			is String -> obj.ifBlank { null }
			is JSONObject -> obj.optString("value").ifBlank { null }
			else -> null
		}
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

	private suspend fun callGrpc(method: String, payload: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val frame = buildGrpcFrame(payload)
			val request = Request.Builder()
				.url("https://api2.wuxiaworld.com/$method")
				.header("User-Agent", DEFAULT_USER_AGENT)
				.header("Content-Type", "application/grpc-web+proto")
				.header("x-grpc-web", "1")
				.post(frame.toRequestBody("application/grpc-web+proto".toMediaType()))
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) return@runCatchingCancellable null
				response.body?.bytes()
			}
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	private fun buildGrpcFrame(payload: ByteArray): ByteArray {
		val len = payload.size
		val frame = ByteArray(5 + len)
		frame[0] = 0x00
		frame[1] = (len ushr 24).toByte()
		frame[2] = (len ushr 16).toByte()
		frame[3] = (len ushr 8).toByte()
		frame[4] = len.toByte()
		System.arraycopy(payload, 0, frame, 5, len)
		return frame
	}

	private fun encodeVarint(v: Long): ByteArray {
		val bos = ByteArrayOutputStream()
		var value = v
		while ((value and 0x7FL.inv()) != 0L) {
			bos.write(((value and 0x7F) or 0x80).toInt())
			value = value ushr 7
		}
		bos.write((value and 0x7F).toInt())
		return bos.toByteArray()
	}

	private fun buildSearchNovelsRequest(
		query: String?,
		sortType: Int,
		sortDirection: Int,
		count: Int,
		searchAfterId: Int? = null,
	): ByteArray {
		val bos = ByteArrayOutputStream()
		if (!query.isNullOrBlank()) {
			val queryBytes = query.toByteArray(Charsets.UTF_8)
			val strVal = ByteArrayOutputStream()
			strVal.write(0x0A)
			strVal.write(encodeVarint(queryBytes.size.toLong()))
			strVal.write(queryBytes)
			val strValBytes = strVal.toByteArray()
			bos.write(0x0A)
			bos.write(encodeVarint(strValBytes.size.toLong()))
			bos.write(strValBytes)
		}
		// sortType (field 4, wire 0)
		bos.write(0x20)
		bos.write(encodeVarint(sortType.toLong()))
		// sortDirection (field 5, wire 0)
		bos.write(0x28)
		bos.write(encodeVarint(sortDirection.toLong()))
		// searchAfterId (field 6, wire 2: Int32Value: field 1 = id)
		if (searchAfterId != null && searchAfterId > 0) {
			val idBytes = encodeVarint(searchAfterId.toLong())
			val msg = ByteArrayOutputStream()
			msg.write(0x08)
			msg.write(idBytes)
			val msgBytes = msg.toByteArray()
			bos.write(0x32)
			bos.write(encodeVarint(msgBytes.size.toLong()))
			bos.write(msgBytes)
		}
		// count (field 7, wire 0)
		bos.write(0x38)
		bos.write(encodeVarint(count.toLong()))
		return bos.toByteArray()
	}

	private fun buildGetNovelRequest(slug: String): ByteArray {
		val slugBytes = slug.toByteArray(Charsets.UTF_8)
		val bos = ByteArrayOutputStream()
		bos.write(0x12) // field 2, wire 2
		bos.write(encodeVarint(slugBytes.size.toLong()))
		bos.write(slugBytes)
		return bos.toByteArray()
	}

	private fun parseSearchNovelsWithLastId(responseBytes: ByteArray): Pair<List<Manga>, Int?> {
		val novels = mutableListOf<Manga>()
		var lastId: Int? = null
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
					if (wireType == 2) {
						val length = reader.readVarint().toInt()
						if (fieldNum == 1) { // NovelItem
							val sub = reader.subReader(length)
							val (manga, id) = parseNovelItemFromProto(sub)
							if (manga != null) {
								novels.add(manga)
							}
							if (id != null) {
								lastId = id
							}
						} else {
							reader.skip(length)
						}
					} else {
						reader.skipByWire(wireType)
					}
				}
			}
			framePos += msgLen
		}
		return novels to lastId
	}

	private fun parseNovelItemFromProto(reader: ProtoReader): Pair<Manga?, Int?> {
		var id = 0
		var name = ""
		var slug = ""
		var status = -1
		var synopsis = ""
		var coverUrl = ""
		var author = ""
		val tags = mutableSetOf<MangaTag>()
		var rating = 0f

		while (reader.hasRemaining()) {
			val tag = reader.readVarint()
			val fieldNum = (tag ushr 3).toInt()
			val wireType = (tag and 0x07).toInt()
			when (wireType) {
				0 -> {
					val v = reader.readVarint().toInt()
					if (fieldNum == 1) id = v
					else if (fieldNum == 4) status = v
				}
				1 -> reader.skip(8)
				2 -> {
					val len = reader.readVarint().toInt()
					when (fieldNum) {
						2 -> name = reader.readString(len)
						3 -> slug = reader.readString(len)
						8 -> if (synopsis.isEmpty()) synopsis = reader.readStringValue(len) else reader.skip(len)
						9 -> synopsis = reader.readStringValue(len)
						10 -> coverUrl = reader.readStringValue(len)
						13 -> author = reader.readStringValue(len)
						15, 16 -> {
							val t = reader.readString(len).trim()
							if (t.isNotEmpty() && t.length > 1) {
								tags.add(MangaTag(title = t, key = t, source = source))
							}
						}
						17 -> rating = reader.readReviewRating(len)
						else -> reader.skip(len)
					}
				}
				5 -> reader.skip(4)
				else -> break
			}
		}

		if (name.isEmpty() && slug.isEmpty()) return null to (if (id > 0) id else null)
		val novelUrl = "$baseUrl/novel/$slug"
		val cleanDesc = synopsis.ifEmpty { null }?.let { Jsoup.parse(it).text().trim().ifBlank { null } }
		val state = when (status) {
			0 -> MangaState.FINISHED
			1 -> MangaState.ONGOING
			2 -> MangaState.PAUSED
			else -> null
		}

		val manga = Manga(
			id = "${source.name}_$novelUrl".longHashCode(),
			title = name.ifBlank { slug },
			altTitle = null,
			url = novelUrl,
			publicUrl = novelUrl,
			rating = rating,
			isNsfw = false,
			coverUrl = coverUrl,
			tags = tags,
			state = state,
			author = author.ifBlank { null },
			largeCoverUrl = coverUrl,
			description = cleanDesc,
			chapters = null,
			source = source,
		)
		return manga to (if (id > 0) id else null)
	}

	private suspend fun fetchChaptersViaGrpc(novelId: Int, novelSlug: String): List<MangaChapter> = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val varintBytes = encodeVarint(novelId.toLong())
			val payload = byteArrayOf(0x08.toByte()) + varintBytes
			val responseBytes = callGrpc("wuxiaworld.api.v2.Chapters/GetChapterList", payload)
				?: return@runCatchingCancellable emptyList()
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

		fun skipByWire(wireType: Int) {
			when (wireType) {
				0 -> readVarint()
				1 -> skip(8)
				2 -> skip(readVarint().toInt())
				5 -> skip(4)
			}
		}

		fun readFixed64(): Long {
			var result = 0L
			for (i in 0 until 8) {
				if (pos >= limit) break
				result = result or ((bytes[pos++].toLong() and 0xFF) shl (i * 8))
			}
			return result
		}

		fun readStringValue(length: Int): String {
			val sub = subReader(length)
			var result = ""
			while (sub.hasRemaining()) {
				val tag = sub.readVarint()
				val fieldNum = (tag ushr 3).toInt()
				val wireType = (tag and 0x07).toInt()
				if (wireType == 2) {
					val strLen = sub.readVarint().toInt()
					if (fieldNum == 1) {
						result = sub.readString(strLen)
					} else {
						sub.skip(strLen)
					}
				} else {
					sub.skipByWire(wireType)
				}
			}
			return result
		}

		fun readReviewRating(length: Int): Float {
			val sub = subReader(length)
			var rating = 0f
			while (sub.hasRemaining()) {
				val tag = sub.readVarint()
				val fieldNum = (tag ushr 3).toInt()
				val wireType = (tag and 0x07).toInt()
				if (wireType == 2) {
					val subLen = sub.readVarint().toInt()
					if (fieldNum == 2) { // rating: DoubleValue
						val dSub = sub.subReader(subLen)
						while (dSub.hasRemaining()) {
							val dTag = dSub.readVarint()
							val dField = (dTag ushr 3).toInt()
							val dWire = (dTag and 0x07).toInt()
							if (dField == 1 && dWire == 1) {
								val bits = dSub.readFixed64()
								val d = java.lang.Double.longBitsToDouble(bits)
								if (!d.isNaN() && d > 0.0) {
									rating = d.toFloat().coerceIn(0f, 1f)
								}
							} else {
								dSub.skipByWire(dWire)
							}
						}
					} else {
						sub.skip(subLen)
					}
				} else {
					sub.skipByWire(wireType)
				}
			}
			return rating
		}

		fun subReader(length: Int): ProtoReader {
			val end = (pos + length).coerceAtMost(limit)
			val sub = ProtoReader(bytes, pos, end)
			pos = end
			return sub
		}
	}
}
