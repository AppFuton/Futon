package io.github.landwarderer.futon.core.parser.tachiyomi

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.parsers.model.ContentRating
import io.github.landwarderer.futon.parsers.model.Manga
import io.github.landwarderer.futon.parsers.model.MangaChapter
import io.github.landwarderer.futon.parsers.model.MangaListFilter
import io.github.landwarderer.futon.parsers.model.MangaListFilterCapabilities
import io.github.landwarderer.futon.parsers.model.MangaListFilterOptions
import io.github.landwarderer.futon.parsers.model.MangaPage
import io.github.landwarderer.futon.parsers.model.MangaState
import io.github.landwarderer.futon.parsers.model.MangaTag
import io.github.landwarderer.futon.parsers.model.SortOrder
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapter that bridges Tachiyomi Source instances to Futon's MangaRepository interface.
 * Uses reflection to call Tachiyomi extension methods and convert between model types.
 */
class TachiyomiMangaRepository(
	override val source: TachiyomiMangaSource,
	private val tachiyomiSource: TachiyomiSource,
	private val cache: MemoryContentCache,
) : MangaRepository {

	private val sourceInstance = tachiyomiSource.sourceInstance
		?: throw IllegalStateException("Tachiyomi source instance not loaded")

	private val sourceClass = sourceInstance.javaClass

	// Tachiyomi supports Popular, Latest, Search - map to our sort orders
	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.RELEVANCE, // For search
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
	)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		return withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val page = (offset / 20) + 1 // Tachiyomi typically uses page numbers, 20 items per page

				val methodName = when (order ?: defaultSortOrder) {
					SortOrder.POPULARITY -> "fetchPopularManga"
					SortOrder.UPDATED -> "fetchLatestUpdates"
					SortOrder.RELEVANCE -> if (filter?.query.isNullOrEmpty()) "fetchPopularManga" else "fetchSearchManga"
					else -> "fetchPopularManga"
				}

				val observable = if (methodName == "fetchSearchManga" && filter != null) {
					// Search: fetchSearchManga(page: Int, query: String, filters: FilterList)
					val query = filter.query ?: ""
					val filterListClass = sourceClass.classLoader?.loadClass("eu.kanade.tachiyomi.source.model.FilterList")
					val emptyFilterList = filterListClass?.getDeclaredConstructor()?.newInstance()

					val method = sourceClass.getMethod(methodName, Int::class.java, String::class.java, filterListClass)
					method.invoke(sourceInstance, page, query, emptyFilterList)
				} else {
					// Popular/Latest: fetchPopularManga(page: Int) / fetchLatestUpdates(page: Int)
					val method = sourceClass.getMethod(methodName, Int::class.java)
					method.invoke(sourceInstance, page)
				}

				// Convert RxJava Observable to coroutine
				val mangasPage = awaitObservable(observable)

				// Extract manga list from MangasPage
				val mangaListField = mangasPage.javaClass.getDeclaredField("mangas")
				mangaListField.isAccessible = true
				val sMangaList = mangaListField.get(mangasPage) as? List<*> ?: emptyList<Any>()

				sMangaList.mapNotNull { sManga ->
					if (sManga != null) convertToFutonManga(sManga) else null
				}
			}.getOrElse { error ->
				error.printStackTraceDebug()
				emptyList()
			}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		return withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val sManga = convertToTachiyomiManga(manga)

				// fetchMangaDetails(manga: SManga): Observable<SManga>
				val method = sourceClass.getMethod("fetchMangaDetails", sManga.javaClass)
				val observable = method.invoke(sourceInstance, sManga)

				val detailedSManga = awaitObservable(observable)

				convertToFutonManga(detailedSManga, manga)
			}.getOrElse { error ->
				error.printStackTraceDebug()
				manga
			}
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val sChapter = convertToTachiyomiChapter(chapter)

				// fetchPageList(chapter: SChapter): Observable<List<Page>>
				val method = sourceClass.getMethod("fetchPageList", sChapter.javaClass)
				val observable = method.invoke(sourceInstance, sChapter)

				val pageList = awaitObservable(observable) as? List<*> ?: emptyList<Any>()

				pageList.mapIndexed { index, page ->
					if (page != null) convertToFutonPage(page, index, chapter) else null
				}.filterNotNull()
			}.getOrElse { error ->
				error.printStackTraceDebug()
				emptyList()
			}
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return withContext(Dispatchers.IO) {
			// Tachiyomi Page already has imageUrl - return as-is
			page.url
		}
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		// Tachiyomi filters are complex - would require significant work to map
		// For now, return empty (basic search still works)
		return MangaListFilterOptions()
	}

	override suspend fun getRelated(seed: Manga): List<Manga> {
		// Tachiyomi sources don't have a standard "related manga" concept
		return emptyList()
	}

	/**
	 * Converts Tachiyomi SManga to Futon Manga model.
	 */
	private fun convertToFutonManga(sManga: Any, existingManga: Manga? = null): Manga {
		val sMangaClass = sManga.javaClass

		val url = getField<String>(sManga, sMangaClass, "url") ?: ""
		val title = getField<String>(sManga, sMangaClass, "title") ?: "Unknown"
		val thumbnailUrl = getField<String>(sManga, sMangaClass, "thumbnail_url") ?: ""
		val description = getField<String>(sManga, sMangaClass, "description")
		val author = getField<String>(sManga, sMangaClass, "author")
		val artist = getField<String>(sManga, sMangaClass, "artist")
		val genre = getField<String>(sManga, sMangaClass, "genre")
		val status = getField<Int>(sManga, sMangaClass, "status") ?: 0

		val tags = genre?.split(",")?.mapNotNull { tag ->
			val trimmed = tag.trim()
			if (trimmed.isNotEmpty()) {
				MangaTag(
					key = trimmed.lowercase(Locale.ROOT),
					title = trimmed,
					source = source,
				)
			} else {
				null
			}
		}?.toSet() ?: emptySet()

		val mangaState = when (status) {
			1 -> MangaState.ONGOING
			2 -> MangaState.FINISHED
			3 -> MangaState.ABANDONED
			else -> null
		}

		return Manga(
			id = generateMangaId(url),
			title = title,
			altTitle = null,
			url = url,
			publicUrl = (tachiyomiSource.baseUrl ?: "") + url,
			rating = existingManga?.rating ?: 0f,
			isNsfw = tachiyomiSource.sourceInstance?.let { isNsfwSource(it) } ?: false,
			coverUrl = thumbnailUrl,
			tags = tags,
			state = mangaState,
			author = author,
			largeCoverUrl = null,
			description = description,
			chapters = existingManga?.chapters, // Preserve existing chapters if updating
			source = source,
			contentRating = if (tachiyomiSource.sourceInstance?.let { isNsfwSource(it) } == true) {
				ContentRating.ADULT
			} else {
				ContentRating.SAFE
			},
		)
	}

	/**
	 * Converts Futon Manga to Tachiyomi SManga.
	 */
	private fun convertToTachiyomiManga(manga: Manga): Any {
		val sMangaClass = sourceClass.classLoader?.loadClass("eu.kanade.tachiyomi.source.model.SManga")
			?: throw ClassNotFoundException("SManga class not found")

		val sMangaCreateMethod = sMangaClass.getMethod("create")
		val sManga = sMangaCreateMethod.invoke(null)

		setField(sManga, sMangaClass, "url", manga.url)
		setField(sManga, sMangaClass, "title", manga.title)

		return sManga
	}

	/**
	 * Converts Futon MangaChapter to Tachiyomi SChapter.
	 */
	private fun convertToTachiyomiChapter(chapter: MangaChapter): Any {
		val sChapterClass = sourceClass.classLoader?.loadClass("eu.kanade.tachiyomi.source.model.SChapter")
			?: throw ClassNotFoundException("SChapter class not found")

		val sChapterCreateMethod = sChapterClass.getMethod("create")
		val sChapter = sChapterCreateMethod.invoke(null)

		setField(sChapter, sChapterClass, "url", chapter.url)
		setField(sChapter, sChapterClass, "name", chapter.name)
		setField(sChapter, sChapterClass, "chapter_number", chapter.number)

		return sChapter
	}

	/**
	 * Converts Tachiyomi Page to Futon MangaPage.
	 */
	private fun convertToFutonPage(page: Any, index: Int, chapter: MangaChapter): MangaPage {
		val pageClass = page.javaClass

		val imageUrl = getField<String>(page, pageClass, "imageUrl") ?: ""
		val url = getField<String>(page, pageClass, "url") ?: imageUrl

		return MangaPage(
			id = generatePageId(chapter.url, index),
			url = url.ifEmpty { imageUrl },
			preview = null,
			source = source,
		)
	}

	/**
	 * Awaits an RxJava Observable and returns the first emitted value.
	 */
	private suspend fun <T> awaitObservable(observable: Any): T = suspendCancellableCoroutine { continuation ->
		try {
			val observableClass = observable.javaClass

			// Call subscribe() method to get the result
			val subscribeMethod = observableClass.getMethod("subscribe", 
				Class.forName("io.reactivex.functions.Consumer"),
				Class.forName("io.reactivex.functions.Consumer"))

			val consumerClass = Class.forName("io.reactivex.functions.Consumer")

			val onNextProxy = java.lang.reflect.Proxy.newProxyInstance(
				consumerClass.classLoader,
				arrayOf(consumerClass)
			) { _, _, args ->
				@Suppress("UNCHECKED_CAST")
				continuation.resume(args[0] as T)
				null
			}

			val onErrorProxy = java.lang.reflect.Proxy.newProxyInstance(
				consumerClass.classLoader,
				arrayOf(consumerClass)
			) { _, _, args ->
				continuation.resumeWithException(args[0] as Throwable)
				null
			}

			val disposable = subscribeMethod.invoke(observable, onNextProxy, onErrorProxy)

			continuation.invokeOnCancellation {
				// Dispose the RxJava subscription on coroutine cancellation
				try {
					disposable?.javaClass?.getMethod("dispose")?.invoke(disposable)
				} catch (e: Exception) {
					e.printStackTraceDebug()
				}
			}
		} catch (e: Exception) {
			continuation.resumeWithException(e)
		}
	}

	/**
	 * Generates a unique manga ID from the URL.
	 */
	private fun generateMangaId(url: String): Long {
		return (source.name + url).hashCode().toLong()
	}

	/**
	 * Generates a unique page ID.
	 */
	private fun generatePageId(chapterUrl: String, index: Int): Long {
		return (source.name + chapterUrl + index).hashCode().toLong()
	}

	/**
	 * Checks if the source is NSFW by checking metadata or class name.
	 */
	private fun isNsfwSource(sourceInstance: Any): Boolean {
		return try {
			// Some sources might have isNsfw() method
			val method = sourceInstance.javaClass.getMethod("isNsfw")
			method.invoke(sourceInstance) as? Boolean ?: false
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * Safely gets a field value using reflection.
	 */
	private inline fun <reified T> getField(obj: Any, clazz: Class<*>, fieldName: String): T? {
		return try {
			val field = clazz.getDeclaredField(fieldName)
			field.isAccessible = true
			field.get(obj) as? T
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Safely sets a field value using reflection.
	 */
	private fun setField(obj: Any, clazz: Class<*>, fieldName: String, value: Any?) {
		try {
			val field = clazz.getDeclaredField(fieldName)
			field.isAccessible = true
			field.set(obj, value)
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}
}
