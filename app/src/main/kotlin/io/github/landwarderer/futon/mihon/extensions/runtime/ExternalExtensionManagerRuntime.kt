package io.github.landwarderer.futon.mihon.extensions.runtime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExternalExtensionManagerRuntime<ResultT, SuccessT, ErrorT, SourceT, WrappedSourceT>(
	private val context: Context,
	private val scope: CoroutineScope,
) {

	private val _installedExtensions = MutableStateFlow<List<SuccessT>>(emptyList())
	val installedExtensions: StateFlow<List<SuccessT>> = _installedExtensions.asStateFlow()

	private val _failedExtensions = MutableStateFlow<List<ErrorT>>(emptyList())
	val failedExtensions: StateFlow<List<ErrorT>> = _failedExtensions.asStateFlow()

	private val _isLoading = MutableStateFlow(false)
	val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

	private val sourceCache = mutableMapOf<Long, SourceT>()
	private val wrappedSourceCache = mutableMapOf<Long, WrappedSourceT>()

	@Volatile
	private var isPackageObserverRegistered = false

	fun initialize(loadAction: suspend () -> Unit) {
		registerPackageObserver(loadAction)
		scope.launchInRuntime(loadAction)
	}

	suspend fun loadExtensions(
		loadResults: suspend (Context) -> List<ResultT>,
		processResults: (List<ResultT>) -> ProcessedExternalExtensions<SuccessT, ErrorT, SourceT, WrappedSourceT>,
	) {
		if (_isLoading.value) return

		_isLoading.value = true
		try {
			sourceCache.clear()
			wrappedSourceCache.clear()
			val processed = processResults(loadResults(context))
			sourceCache.putAll(processed.sourceById)
			wrappedSourceCache.putAll(processed.wrappedSourceById)
			_installedExtensions.value = processed.successful
			_failedExtensions.value = processed.failed
		} finally {
			_isLoading.value = false
		}
	}

	fun getInstalledExtensions(): List<SuccessT> = installedExtensions.value

	fun getSourceById(sourceId: Long): SourceT? = sourceCache[sourceId]

	fun getWrappedSourceById(sourceId: Long): WrappedSourceT? = wrappedSourceCache[sourceId]

	fun getWrappedSources(): List<WrappedSourceT> = wrappedSourceCache.values.toList()

	fun getSourceCount(): Int = sourceCache.size

	fun hasExtensions(): Boolean = installedExtensions.value.isNotEmpty()

	private fun registerPackageObserver(loadAction: suspend () -> Unit) {
		if (isPackageObserverRegistered) return
		registerExternalExtensionPackageObserver(context) {
			loadAction()
		}
		isPackageObserverRegistered = true
	}

	private fun CoroutineScope.launchInRuntime(loadAction: suspend () -> Unit) {
		launch {
			loadAction()
		}
	}
}
