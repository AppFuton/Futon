package io.github.landwarderer.futon.reader.domain

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.collection.LongSparseArray
import androidx.collection.set
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.BitmapImage
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.toBitmap
import com.davemorrissey.labs.subscaleview.ImageSource
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.use
import org.jetbrains.annotations.Blocking
import io.github.landwarderer.futon.core.LocalizedAppContext
import io.github.landwarderer.futon.core.image.BitmapDecoderCompat
import io.github.landwarderer.futon.core.network.CommonHeaders
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.network.imageproxy.ImageProxyInterceptor
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.image.TrimTransformation
import io.github.landwarderer.futon.core.util.FileSize
import io.github.landwarderer.futon.core.util.MimeTypes
import io.github.landwarderer.futon.core.util.ext.URI_SCHEME_ZIP
import io.github.landwarderer.futon.core.util.ext.cancelChildrenAndJoin
import io.github.landwarderer.futon.core.util.ext.compressToPNG
import io.github.landwarderer.futon.core.util.ext.ensureRamAtLeast
import io.github.landwarderer.futon.core.util.ext.ensureSuccess
import io.github.landwarderer.futon.core.util.ext.getCompletionResultOrNull
import io.github.landwarderer.futon.core.util.ext.isFileUri
import io.github.landwarderer.futon.core.util.ext.isNotEmpty
import io.github.landwarderer.futon.core.util.ext.isPowerSaveMode
import io.github.landwarderer.futon.core.util.ext.isZipUri
import io.github.landwarderer.futon.core.util.ext.lifecycleScope
import io.github.landwarderer.futon.core.util.ext.mangaSourceExtra
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.ramAvailable
import io.github.landwarderer.futon.core.util.ext.toMimeType
import io.github.landwarderer.futon.core.util.ext.use
import io.github.landwarderer.futon.core.util.ext.withProgress
import io.github.landwarderer.futon.core.util.progress.ProgressDeferred
import io.github.landwarderer.futon.download.ui.worker.DownloadSlowdownDispatcher
import io.github.landwarderer.futon.local.data.LocalStorageCache
import io.github.landwarderer.futon.local.data.PageCache
import io.github.landwarderer.futon.parsers.model.MangaPage
import io.github.landwarderer.futon.parsers.model.MangaSource
import io.github.landwarderer.futon.parsers.util.requireBody
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import java.io.File
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ActivityRetainedScoped
class PageLoader @Inject constructor(
	@LocalizedAppContext private val context: Context,
	lifecycle: ActivityRetainedLifecycle,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val coil: ImageLoader,
	private val settings: AppSettings,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	private val downloadSlowdownDispatcher: DownloadSlowdownDispatcher,
) {

	val loaderScope = lifecycle.lifecycleScope + InternalErrorHandler() + Dispatchers.IO

	private val tasks = LongSparseArray<ProgressDeferred<Uri, Float>>()
	private val semaphore = Semaphore(3)
	private val convertLock = Mutex()
	private val prefetchLock = Mutex()

	@Volatile
	private var repository: MangaRepository? = null
	private val prefetchQueue = LinkedList<MangaPage>()
	private val counter = AtomicInteger(0)
	private var prefetchQueueLimit = PREFETCH_LIMIT_DEFAULT // TODO adaptive
	private val edgeDetector = EdgeDetector(context)

	fun isPrefetchApplicable(): Boolean {
		return repository is CachingMangaRepository
			&& settings.isPagesPreloadEnabled
			&& !context.isPowerSaveMode()
			&& !isLowRam()
	}

	@AnyThread
	fun prefetch(pages: List<ReaderPage>) = loaderScope.launch {
		prefetchLock.withLock {
			for (page in pages.asReversed()) {
				if (tasks.containsKey(page.id)) {
					continue
				}
				prefetchQueue.offerFirst(page.toMangaPage())
				if (prefetchQueue.size > prefetchQueueLimit) {
					prefetchQueue.pollLast()
				}
			}
		}
		if (counter.get() == 0) {
			onIdle()
		}
	}

	suspend fun loadPreview(page: MangaPage): ImageSource? {
		val preview = page.preview
		if (preview.isNullOrEmpty()) {
			return null
		}
		val request = ImageRequest.Builder(context)
			.data(preview)
			.mangaSourceExtra(page.source)
			.transformations(TrimTransformation())
			.build()
		return coil.execute(request).image?.toImageSource()
	}

	fun peekPreviewSource(preview: String?): ImageSource? {
		if (preview.isNullOrEmpty()) {
			return null
		}
		coil.memoryCache?.let { cache ->
			val key = MemoryCache.Key(preview)
			cache[key]?.image?.let {
				return if (it is BitmapImage) {
					ImageSource.cachedBitmap(it.toBitmap())
				} else {
					ImageSource.bitmap(it.toBitmap())
				}
			}
		}
		coil.diskCache?.let { cache ->
			cache.openSnapshot(preview)?.use { snapshot ->
				return ImageSource.file(snapshot.data.toFile())
			}
		}
		return null
	}

	fun loadPageAsync(page: MangaPage, force: Boolean): ProgressDeferred<Uri, Float> {
		var task = tasks[page.id]?.takeIf { it.isValid() }
		if (force) {
			task?.cancel()
		} else if (task?.isCancelled == false) {
			return task
		}
		task = loadPageAsyncImpl(page, skipCache = force, isPrefetch = false)
		synchronized(tasks) {
			tasks[page.id] = task
		}
		return task
	}

    suspend fun updateCache(page: MangaPage) {
        val progress = MutableStateFlow(PROGRESS_UNDEFINED)
        loadPageImpl(page, progress, isPrefetch = false, skipCache = true)
    }

	suspend fun loadPage(page: MangaPage, force: Boolean): Uri {
		return loadPageAsync(page, force).await()
	}

	@CheckResult
	suspend fun convertBimap(uri: Uri): Uri = convertLock.withLock {
		if (uri.isZipUri()) {
			runInterruptible(Dispatchers.IO) {
				ZipFile(uri.schemeSpecificPart).use { zip ->
					val entry = zip.getEntry(uri.fragment)
					context.ensureRamAtLeast(entry.size * 2)
					zip.getInputStream(entry).use {
						BitmapDecoderCompat.decode(it, MimeTypes.getMimeTypeFromExtension(entry.name))
					}
				}
			}.use { image ->
				cache.set(uri.toString(), image).toUri()
			}
		} else {
			val file = uri.toFile()
			runInterruptible(Dispatchers.IO) {
				context.ensureRamAtLeast(file.length() * 2)
				BitmapDecoderCompat.decode(file)
			}.use { image ->
				image.compressToPNG(file)
			}
			uri
		}
	}

	suspend fun getTrimmedBounds(uri: Uri): Rect? = runCatchingCancellable {
		edgeDetector.getBounds(ImageSource.uri(uri))
	}.onFailure { error ->
		error.printStackTraceDebug()
	}.getOrNull()

	suspend fun getPageUrl(page: MangaPage): String {
		return getRepository(page.source).getPageUrl(page)
	}

	suspend fun invalidate(clearCache: Boolean) {
		tasks.clear()
		loaderScope.cancelChildrenAndJoin()
		if (clearCache) {
			cache.clear()
		}
	}

	private fun onIdle() = loaderScope.launch {
		prefetchLock.withLock {
			while (prefetchQueue.isNotEmpty()) {
				val page = prefetchQueue.pollFirst() ?: return@launch
				synchronized(tasks) {
					tasks[page.id] = loadPageAsyncImpl(page, skipCache = false, isPrefetch = true)
				}
			}
		}
	}

	private fun loadPageAsyncImpl(
		page: MangaPage,
		skipCache: Boolean,
		isPrefetch: Boolean,
	): ProgressDeferred<Uri, Float> {
		val progress = MutableStateFlow(PROGRESS_UNDEFINED)
		val deferred = loaderScope.async {
			counter.incrementAndGet()
			try {
				loadPageImpl(
					page = page,
					progress = progress,
					isPrefetch = isPrefetch,
					skipCache = skipCache,
				)
			} finally {
				if (counter.decrementAndGet() == 0) {
					onIdle()
				}
			}
		}
		return ProgressDeferred(deferred, progress)
	}

	@Synchronized
	private fun getRepository(source: MangaSource): MangaRepository {
		val result = repository
		return if (result != null && result.source == source) {
			result
		} else {
			mangaRepositoryFactory.create(source).also { repository = it }
		}
	}

	private suspend fun loadPageImpl(
		page: MangaPage,
		progress: MutableStateFlow<Float>,
		isPrefetch: Boolean,
		skipCache: Boolean,
	): Uri = semaphore.withPermit {
		val pageUrl = getPageUrl(page)
		check(pageUrl.isNotBlank()) { "Cannot obtain full image url for $page" }
		if (!skipCache) {
			cache[pageUrl]?.let { return it.toUri() }
		}
		val uri = pageUrl.toUri()
		return when {
			uri.isZipUri() -> if (uri.scheme == URI_SCHEME_ZIP) {
				uri
			} else { // legacy uri
				uri.buildUpon().scheme(URI_SCHEME_ZIP).build()
			}

			uri.isFileUri() -> uri
			else -> {
				if (isPrefetch) {
					downloadSlowdownDispatcher.delay(page.source)
				}
				val request = createPageRequest(pageUrl, page.source)
				imageProxyInterceptor.interceptPageRequest(request, okHttp).ensureSuccess().use { response ->
					response.requireBody().withProgress(progress).use {
						cache.set(pageUrl, it.source(), it.contentType()?.toMimeType())
					}
				}.toUri()
			}
		}
	}

	private fun isLowRam(): Boolean {
		return context.ramAvailable <= FileSize.MEGABYTES.convert(PREFETCH_MIN_RAM_MB, FileSize.BYTES)
	}

	private fun Image.toImageSource(): ImageSource = if (this is BitmapImage) {
		ImageSource.cachedBitmap(toBitmap())
	} else {
		ImageSource.bitmap(toBitmap())
	}

	private fun Deferred<Uri>.isValid(): Boolean {
		return getCompletionResultOrNull()?.map { uri ->
			uri.exists() && uri.isTargetNotEmpty()
		}?.getOrDefault(false) != false
	}

	private class InternalErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
		CoroutineExceptionHandler {

		override fun handleException(context: CoroutineContext, exception: Throwable) {
			exception.printStackTraceDebug()
		}
	}

	companion object {

		private const val PROGRESS_UNDEFINED = -1f
		private const val PREFETCH_LIMIT_DEFAULT = 6
		private const val PREFETCH_MIN_RAM_MB = 80L

		fun createPageRequest(pageUrl: String, mangaSource: MangaSource) = Request.Builder()
			.url(pageUrl)
			.get()
			.header(CommonHeaders.ACCEPT, "image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8")
			.cacheControl(CommonHeaders.CACHE_CONTROL_NO_STORE)
			.tag(MangaSource::class.java, mangaSource)
			.build()


		@Blocking
		private fun Uri.exists(): Boolean = when {
			isFileUri() -> toFile().exists()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { it.getEntry(fragment) != null }
			}

			else -> false
		}

		@Blocking
		private fun Uri.isTargetNotEmpty(): Boolean = when {
			isFileUri() -> toFile().isNotEmpty()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { (it.getEntry(fragment)?.size ?: 0L) != 0L }
			}

			else -> false
		}
	}
}
