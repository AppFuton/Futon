package io.github.landwarderer.futon.reader.ui.pager.vm

import android.graphics.Rect
import android.net.Uri
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.throttle
import org.koitharu.kotatsu.parsers.model.MangaPage
import io.github.landwarderer.futon.reader.domain.PageLoader
import io.github.landwarderer.futon.reader.ui.config.ReaderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.IOException

class PageViewModel(
	private val loader: PageLoader,
	val settingsProducer: ReaderSettings.Producer,
	private val networkState: NetworkState,
	private val exceptionResolver: ExceptionResolver,
	private val isWebtoon: Boolean,
) : DefaultOnImageEventListener {

	private val scope = loader.loaderScope + Dispatchers.Main.immediate
	private var job: Job? = null
	private var cachedBounds: Rect? = null

	val state = MutableStateFlow<PageState>(PageState.Empty)

	fun isLoading() = job?.isActive == true

	fun onBind(page: MangaPage) {
		val prevJob = job
		job = scope.launch(Dispatchers.IO) {
			prevJob?.cancelAndJoin()
			doLoad(page, force = false)
		}
	}

	fun retry(page: MangaPage, isFromUser: Boolean) {
		val prevJob = job
		job = scope.launch {
			prevJob?.cancelAndJoin()
			val e = (state.value as? PageState.Error)?.error
			if (e != null && ExceptionResolver.canResolve(e)) {
				if (isFromUser) {
					exceptionResolver.resolve(e)
				}
			}
			withContext(Dispatchers.IO) {
				doLoad(page, force = true)
			}
		}
	}

	fun showErrorDetails(url: String?) {
		val e = (state.value as? PageState.Error)?.error ?: return
		exceptionResolver.showErrorDetails(e, url)
	}

	fun onRecycle() {
		state.value = PageState.Empty
		cachedBounds = null
		job?.cancel()
	}

	override fun onImageLoaded() {
		state.update { currentState ->
			if (currentState is PageState.Loaded) {
				PageState.Shown(currentState.source, currentState.isConverted)
			} else {
				currentState
			}
		}
	}

	override fun onImageLoadError(e: Throwable) {
		e.printStackTraceDebug("PageViewModel::onImageLoadError")

		state.update { currentState ->
			if (currentState is PageState.Loaded) {
				val uri = (currentState.source as? ImageSource.Uri)?.uri
				if (!currentState.isConverted && uri != null && e is IOException) {
					tryConvert(uri, e)
					PageState.Converting()
				} else {
					PageState.Error(e)
				}
			} else {
				currentState
			}
		}
	}

	private fun tryConvert(uri: Uri, e: Exception) {
		val prevJob = job
		job = scope.launch(Dispatchers.IO) {
			prevJob?.join()
			state.value = PageState.Converting()
			try {
				val newUri = loader.convertBimap(uri)
				cachedBounds = if (settingsProducer.value.isPagesCropEnabled(isWebtoon)) {
					loader.getTrimmedBounds(newUri)
				} else {
					null
				}
				state.value = PageState.Loaded(newUri.toImageSource(cachedBounds), isConverted = true)
			} catch (ce: CancellationException) {
				throw ce
			} catch (e2: Throwable) {
				e2.printStackTrace()
				e.addSuppressed(e2)
				state.value = PageState.Error(e)
			}
		}
	}

	@WorkerThread
	private suspend fun doLoad(data: MangaPage, force: Boolean) = coroutineScope {
		state.value = PageState.Loading(null, -1)
		val previewJob = launch {
			val preview = loader.loadPreview(data) ?: return@launch
			state.update {
				if (it is PageState.Loading) it.copy(preview = preview) else it
			}
		}
		try {
			val task = loader.loadPageAsync(data, force)
			val progressObserver = observeProgress(this, task.progressAsFlow())
			val uri = task.await()
			progressObserver.cancelAndJoin()
			previewJob.cancel()
			cachedBounds = if (settingsProducer.value.isPagesCropEnabled(isWebtoon)) {
				loader.getTrimmedBounds(uri)
			} else {
				null
			}
			state.value = PageState.Loaded(uri.toImageSource(cachedBounds), isConverted = false)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug("PageViewModel::doLoad")
			state.value = PageState.Error(e)
			if (e is IOException && !networkState.value) {
				networkState.awaitForConnection()
				retry(data, isFromUser = false)
			}
		}
	}

	private fun observeProgress(scope: CoroutineScope, progress: Flow<Float>) = progress
		.throttle(250)
		.onEach {
			val progressValue = (100 * it).toInt()
			state.update { currentState ->
				if (currentState is PageState.Loading) {
					currentState.copy(progress = progressValue)
				} else {
					currentState
				}
			}
		}.launchIn(scope)

	private fun Uri.toImageSource(bounds: Rect?): ImageSource {
		val source = ImageSource.uri(this)
		return if (bounds != null) {
			source.region(bounds)
		} else {
			source
		}
	}
}
