package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

abstract class BaseNovelRepository(
	cache: MemoryContentCache,
	protected val httpClient: OkHttpClient,
	override val source: NovelParserSource,
) : CachingMangaRepository(cache) {

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities()

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	protected suspend fun fetchDocument(
		url: String,
		headers: Map<String, String> = emptyMap(),
	): Document = withContext(Dispatchers.IO) {
		val requestBuilder = Request.Builder()
			.url(url)
			.header("User-Agent", DEFAULT_USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
			.header("Accept-Language", "en-US,en;q=0.9")

		for ((key, value) in headers) {
			requestBuilder.header(key, value)
		}

		httpClient.newCall(requestBuilder.build()).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("HTTP error ${response.code} fetching $url")
			}
			val body = response.body?.string().orEmpty()
			Jsoup.parse(body, url)
		}
	}

	protected suspend fun fetchString(
		url: String,
		headers: Map<String, String> = emptyMap(),
	): String = withContext(Dispatchers.IO) {
		val requestBuilder = Request.Builder()
			.url(url)
			.header("User-Agent", DEFAULT_USER_AGENT)
			.header("Accept", "*/*")

		for ((key, value) in headers) {
			requestBuilder.header(key, value)
		}

		httpClient.newCall(requestBuilder.build()).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("HTTP error ${response.code} fetching $url")
			}
			response.body?.string().orEmpty()
		}
	}

	companion object {
		const val DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
	}
}
