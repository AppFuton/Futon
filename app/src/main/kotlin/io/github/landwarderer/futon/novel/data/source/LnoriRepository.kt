package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.util.Locale

class LnoriRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.LNORI) {

	private val baseUrl = source.defaultUrl

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
		isMultipleTagsSupported = true,
		isTagsExclusionSupported = true,
	)

	private data class LnoriEntry(
		val manga: Manga,
		val year: Int,
		val volumes: Int,
		val relevance: Int,
		val tags: Set<String>,
		val author: String,
	)

	companion object {
		private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
		private val catalogMutex = Mutex()
		private val fetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

		@Volatile
		private var cachedCatalog: List<LnoriEntry>? = null

		@Volatile
		private var lastCacheTime: Long = 0L

		@Volatile
		private var inFlightFetch: Deferred<List<LnoriEntry>>? = null

		// Exposed for unit tests
		internal fun clearCache() {
			cachedCatalog = null
			lastCacheTime = 0L
			inFlightFetch = null
		}
	}

	private suspend fun getOrFetchCatalog(): List<LnoriEntry> {
		val cached = cachedCatalog
		val now = System.currentTimeMillis()
		if (cached != null && (now - lastCacheTime) < CACHE_TTL_MS) {
			return cached
		}
		val deferred = catalogMutex.withLock {
			val current = cachedCatalog
			if (current != null && (System.currentTimeMillis() - lastCacheTime) < CACHE_TTL_MS) {
				return current
			}
			inFlightFetch ?: fetchScope.async {
				try {
					val fresh = fetchCatalogFromWeb()
					cachedCatalog = fresh
					lastCacheTime = System.currentTimeMillis()
					fresh
				} finally {
					catalogMutex.withLock {
						inFlightFetch = null
					}
				}
			}.also { inFlightFetch = it }
		}
		return deferred.await()
	}

	private suspend fun fetchCatalogFromWeb(): List<LnoriEntry> {
		val doc = fetchDocument("$baseUrl/library")
		val cards = doc.select("article.card")
		val result = mutableListOf<LnoriEntry>()

		for (card in cards) {
			val title = card.attr("data-t").trim().ifEmpty {
				card.selectFirst(".card-title, h2, h3")?.text()?.trim().orEmpty()
			}
			if (title.isEmpty()) continue

			val author = card.attr("data-a").trim().ifEmpty {
				card.selectFirst(".popup-author")?.text()?.substringBefore('(')?.trim().orEmpty()
			}
			val year = card.attr("data-d").toIntOrNull() ?: 0
			val volumes = card.attr("data-v").toIntOrNull() ?: 0
			val relevance = card.attr("data-rel").toIntOrNull() ?: 0

			val rawTags = card.attr("data-tags")
			val tagSet = if (rawTags.isNotEmpty()) {
				rawTags.split(',')
					.map { it.trim().lowercase(Locale.ROOT) }
					.filter { it.isNotEmpty() }
					.toSet()
			} else {
				emptySet()
			}

			val mangaTags = tagSet.map { key ->
				val formattedTitle = key.split(" ").joinToString(" ") { word ->
					word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
				}
				MangaTag(
					title = formattedTitle,
					key = key,
					source = source,
				)
			}.toSet()

			val linkEl = card.selectFirst("a[href*='/series/']") ?: card.selectFirst("a")
			val href = linkEl?.absUrl("href")?.ifEmpty {
				val rawHref = linkEl.attr("href")
				if (rawHref.startsWith("/")) "$baseUrl$rawHref" else rawHref
			} ?: ""
			if (href.isBlank()) continue

			val coverEl = card.selectFirst("img")
			val coverUrl = coverEl?.absUrl("src")?.ifEmpty { coverEl.attr("src") }.orEmpty()
			val desc = card.selectFirst(".popup-description")?.text()?.trim()

			val manga = Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitle = null,
				url = href,
				publicUrl = href,
				rating = 0f,
				isNsfw = false,
				coverUrl = coverUrl,
				tags = mangaTags,
				state = null,
				author = author.ifBlank { null },
				largeCoverUrl = coverUrl,
				description = desc,
				chapters = null,
				source = source,
			)

			result.add(
				LnoriEntry(
					manga = manga,
					year = year,
					volumes = volumes,
					relevance = relevance,
					tags = tagSet,
					author = author,
				)
			)
		}
		return result
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		val catalog = getOrFetchCatalog()
		val allTags = catalog.flatMap { it.tags }.toSet().sorted()
		val mangaTags = allTags.map { key ->
			val formattedTitle = key.split(" ").joinToString(" ") { word ->
				word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
			}
			MangaTag(
				title = formattedTitle,
				key = key,
				source = source,
			)
		}.toSet()
		return MangaListFilterOptions(
			availableTags = mangaTags,
		)
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val catalog = getOrFetchCatalog()
		var filtered = catalog

		// 1. Query filter
		val query = filter?.query?.trim().orEmpty()
		if (query.isNotEmpty()) {
			filtered = filtered.filter { entry ->
				entry.manga.title.contains(query, ignoreCase = true) ||
					entry.author.contains(query, ignoreCase = true)
			}
		}

		// 2. Tag filter (Include)
		val filterTags = filter?.tags
		if (!filterTags.isNullOrEmpty()) {
			val reqKeys = filterTags.map { it.key.lowercase(Locale.ROOT) }
			val reqTitles = filterTags.map { it.title.lowercase(Locale.ROOT) }
			filtered = filtered.filter { entry ->
				reqKeys.all { it in entry.tags } || reqTitles.all { it in entry.tags }
			}
		}

		// 3. Tag filter (Exclude)
		val filterTagsExclude = filter?.tagsExclude
		if (!filterTagsExclude.isNullOrEmpty()) {
			val excKeys = filterTagsExclude.map { it.key.lowercase(Locale.ROOT) }
			val excTitles = filterTagsExclude.map { it.title.lowercase(Locale.ROOT) }
			filtered = filtered.filterNot { entry ->
				excKeys.any { it in entry.tags } || excTitles.any { it in entry.tags }
			}
		}

		// 4. Stable sorting
		val effectiveOrder = order ?: defaultSortOrder
		val sorted = when (effectiveOrder) {
			SortOrder.NEWEST, SortOrder.UPDATED -> {
				filtered.sortedWith(
					compareByDescending<LnoriEntry> { it.year }
						.thenByDescending { it.relevance }
						.thenBy { it.manga.id }
				)
			}
			SortOrder.ALPHABETICAL -> {
				filtered.sortedWith(
					compareBy<LnoriEntry> { it.manga.title.lowercase(Locale.ROOT) }
						.thenBy { it.manga.id }
				)
			}
			SortOrder.RELEVANCE -> {
				if (query.isNotEmpty()) {
					filtered.sortedWith(
						compareByDescending<LnoriEntry> { it.manga.title.equals(query, ignoreCase = true) }
							.thenByDescending { it.manga.title.startsWith(query, ignoreCase = true) }
							.thenByDescending { it.relevance }
							.thenBy { it.manga.title }
							.thenBy { it.manga.id }
					)
				} else {
					filtered.sortedWith(
						compareByDescending<LnoriEntry> { it.relevance }
							.thenBy { it.manga.title }
							.thenBy { it.manga.id }
					)
				}
			}
			SortOrder.POPULARITY -> {
				filtered.sortedWith(
					compareByDescending<LnoriEntry> { it.relevance }
						.thenBy { it.manga.title }
						.thenBy { it.manga.id }
				)
			}
			else -> {
				filtered.sortedWith(
					compareByDescending<LnoriEntry> { it.relevance }
						.thenBy { it.manga.title }
						.thenBy { it.manga.id }
				)
			}
		}

		// 5. Pagination
		return sorted.drop(offset).take(20).map { it.manga }
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val title = doc.selectFirst("h1.series-title, h1")?.text()?.trim() ?: manga.title
		val cover = doc.selectFirst(".series-cover img, figure img, img")?.absUrl("src")?.ifEmpty { manga.coverUrl } ?: manga.coverUrl
		val desc = doc.selectFirst(".series-synopsis, .series-description, p.synopsis, p.description")?.text()?.trim() ?: manga.description
		val author = doc.selectFirst(".series-author a, .series-author")?.text()?.trim() ?: manga.author

		// 1. Try JSON-LD Schema.org hasPart
		val scriptEls = doc.select("script[type='application/ld+json']")
		var jsonChapters: List<MangaChapter>? = null

		for (scriptEl in scriptEls) {
			val jsonStr = scriptEl.data().trim()
			if (!jsonStr.contains("hasPart") && !jsonStr.contains("Book")) continue
			try {
				val root = JSONObject(jsonStr)
				val hasPartObj = root.opt("hasPart") ?: continue
				val bookList = mutableListOf<JSONObject>()
				if (hasPartObj is JSONArray) {
					for (i in 0 until hasPartObj.length()) {
						val item = hasPartObj.optJSONObject(i)
						if (item != null) bookList.add(item)
					}
				} else if (hasPartObj is JSONObject) {
					bookList.add(hasPartObj)
				}

				if (bookList.isNotEmpty()) {
					jsonChapters = bookList.mapIndexedNotNull { index, book ->
						val bookUrl = book.optString("url").trim()
						if (bookUrl.isEmpty()) return@mapIndexedNotNull null
						val bookName = book.optString("name").trim().ifEmpty { "Volume ${index + 1}" }
						val positionStr = book.optString("position").trim()
						val posNumber = positionStr.toFloatOrNull() ?: (index + 1).toFloat()

						MangaChapter(
							id = "${source.name}_$bookUrl".longHashCode(),
							title = bookName,
							number = posNumber,
							volume = posNumber.toInt(),
							url = bookUrl,
							uploadDate = 0L,
							source = source,
							scanlator = null,
							branch = null,
						)
					}
					if (!jsonChapters.isNullOrEmpty()) {
						break
					}
				}
			} catch (_: Exception) {
				// Fall back to DOM parsing
			}
		}

		// 2. DOM fallback
		val domChapters = mutableListOf<MangaChapter>()
		val volCards = doc.select(".vol-grid article.card, section.vol-grid article.card")
		if (volCards.isNotEmpty()) {
			volCards.forEachIndexed { index, card ->
				val linkEl = card.selectFirst("a[href*='/book/']") ?: return@forEachIndexed
				val bookHref = linkEl.absUrl("href").ifEmpty {
					val raw = linkEl.attr("href")
					if (raw.startsWith("/")) "$baseUrl$raw" else raw
				}
				val volumeTitle = card.selectFirst(".card-title, h2, h3")?.text()?.trim()
					?.ifEmpty { linkEl.attr("aria-label").trim() }
					?.ifEmpty { "Volume ${index + 1}" }
					?: "Volume ${index + 1}"
				val pos = (index + 1).toFloat()
				domChapters.add(
					MangaChapter(
						id = "${source.name}_$bookHref".longHashCode(),
						title = volumeTitle,
						number = pos,
						volume = pos.toInt(),
						url = bookHref,
						uploadDate = 0L,
						source = source,
						scanlator = null,
						branch = null,
					)
				)
			}
		}

		if (domChapters.isEmpty()) {
			val seenUrls = mutableSetOf<String>()
			doc.select("a[href*='/book/']").forEach { a ->
				val bookHref = a.absUrl("href").ifEmpty {
					val raw = a.attr("href")
					if (raw.startsWith("/")) "$baseUrl$raw" else raw
				}
				if (bookHref.isBlank() || !seenUrls.add(bookHref)) return@forEach
				val text = a.text().trim()
				val titleText = if (text.equals("Start Reading", ignoreCase = true) || text.isEmpty()) {
					a.attr("aria-label").trim().ifEmpty { "Volume ${seenUrls.size}" }
				} else {
					text
				}
				val idx = seenUrls.size
				domChapters.add(
					MangaChapter(
						id = "${source.name}_$bookHref".longHashCode(),
						title = titleText,
						number = idx.toFloat(),
						volume = idx,
						url = bookHref,
						uploadDate = 0L,
						source = source,
						scanlator = null,
						branch = null,
					)
				)
			}
		}

		if (domChapters.isEmpty() && manga.url.contains("/book/")) {
			domChapters.add(
				MangaChapter(
					id = "${source.name}_${manga.url}".longHashCode(),
					title = manga.title.ifBlank { "Complete Volume" },
					number = 1f,
					volume = 1,
					url = manga.url,
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				)
			)
		}

		val chapters = (if (!jsonChapters.isNullOrEmpty()) jsonChapters else domChapters).ifEmpty {
			listOf(
				MangaChapter(
					id = "${source.name}_${manga.url}".longHashCode(),
					title = "Complete Volume",
					number = 1f,
					volume = 1,
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
			authors = author?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: manga.authors,
			chapters = chapters,
		)
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val rawUrl = chapter.url.substringBefore('#')
		val doc = fetchDocument(rawUrl)

		val container = doc.selectFirst("article.content-body, article.reader-content, article, main") ?: doc.body()
		container.select("script, style, nav, header, footer, noscript, .ad, .ads, .advertisement").remove()

		for (img in container.select("img")) {
			var src = img.attr("src")
			if (src.startsWith("/")) {
				src = "$baseUrl$src"
			}
			if (src.contains("img.lnori.com", ignoreCase = true)) {
				src = src.replace(Regex("""\.(jpe?g)(\?.*)?$""", RegexOption.IGNORE_CASE)) { matchResult ->
					val query = matchResult.groups[2]?.value.orEmpty()
					".avif$query"
				}
			}
			img.attr("src", src)
		}

		for (sourceTag in container.select("source")) {
			var srcset = sourceTag.attr("srcset")
			if (srcset.startsWith("/")) {
				srcset = "$baseUrl$srcset"
			}
			if (srcset.contains("img.lnori.com", ignoreCase = true)) {
				srcset = srcset.replace(Regex("""\.(jpe?g)(\?.*)?$""", RegexOption.IGNORE_CASE)) { matchResult ->
					val query = matchResult.groups[2]?.value.orEmpty()
					".avif$query"
				}
			}
			sourceTag.attr("srcset", srcset)
		}

		return listOf(
			MangaPage(
				id = chapter.id,
				url = container.html(),
				preview = null,
				source = source,
			)
		)
	}
}
