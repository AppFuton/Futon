package io.github.landwarderer.futon.core.parser

import android.util.Log
import androidx.collection.MutableLongSet
import coil3.request.CachePolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.cache.SafeDeferred
import io.github.landwarderer.futon.core.util.MultiMutex
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.parsers.model.Manga
import io.github.landwarderer.futon.parsers.model.MangaChapter
import io.github.landwarderer.futon.parsers.model.MangaPage
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable

abstract class CachingMangaRepository(
	private val cache: MemoryContentCache,
) : MangaRepository {

	private val detailsMutex = MultiMutex<Long>()
	private val relatedMangaMutex = MultiMutex<Long>()
	private val pagesMutex = MultiMutex<Long>()

	final override suspend fun getDetails(manga: Manga): Manga = getDetails(manga, CachePolicy.ENABLED)

	final override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = pagesMutex.withLock(chapter.id) {
		cache.getPages(source, chapter.url)?.let { return it }
		val pages = asyncSafe {
			getPagesImpl(chapter).distinctById()
		}
		cache.putPages(source, chapter.url, pages)
		pages
	}.await()

	final override suspend fun getRelated(seed: Manga): List<Manga> = relatedMangaMutex.withLock(seed.id) {
		cache.getRelatedManga(source, seed.url)?.let { return it }
		val related = asyncSafe {
			getRelatedMangaImpl(seed).filterNot { it.id == seed.id }
		}
		cache.putRelatedManga(source, seed.url, related)
		related
	}.await()

	suspend fun getDetails(manga: Manga, cachePolicy: CachePolicy): Manga = detailsMutex.withLock(manga.id) {
		if (cachePolicy.readEnabled) {
			cache.getDetails(source, manga.url)?.let { return it }
		}
		val details = asyncSafe {
			getDetailsImpl(manga)
		}
		if (cachePolicy.writeEnabled) {
			cache.putDetails(source, manga.url, details)
		}
		details
	}.await()

	suspend fun peekDetails(manga: Manga): Manga? {
		return cache.getDetails(source, manga.url)
	}

	fun invalidateCache() {
		cache.clear(source)
	}

	protected abstract suspend fun getDetailsImpl(manga: Manga): Manga

	protected abstract suspend fun getRelatedMangaImpl(seed: Manga): List<Manga>

	protected abstract suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage>

	private suspend fun <T> asyncSafe(block: suspend CoroutineScope.() -> T): SafeDeferred<T> {
		var dispatcher = currentCoroutineContext()[CoroutineDispatcher.Key]
		if (dispatcher == null || dispatcher is MainCoroutineDispatcher) {
			dispatcher = Dispatchers.IO
		}
		return SafeDeferred(
			processLifecycleScope.async(dispatcher) {
				runCatchingCancellable { block() }
			},
		)
	}

	private fun List<MangaPage>.distinctById(): List<MangaPage> {
		if (isEmpty()) {
			return emptyList()
		}
		val result = ArrayList<MangaPage>(size)
		val set = MutableLongSet(size)
		for (page in this) {
			if (set.add(page.id)) {
				result.add(page)
			} else if (BuildConfig.DEBUG) {
				Log.w(null, "Duplicate page: $page")
			}
		}
		return result
	}
}
