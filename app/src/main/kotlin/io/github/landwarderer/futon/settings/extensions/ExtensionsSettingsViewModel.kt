package io.github.landwarderer.futon.settings.extensions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsSettingsViewModel @Inject constructor(
	private val extensionRepository: TachiyomiExtensionRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val _extensionsCount = MutableStateFlow(0)
	val extensionsCount: StateFlow<Int> = _extensionsCount

	init {
		loadExtensionsCount()
	}

	fun onExtensionsEnabledChanged() {
		loadExtensionsCount()
	}

	private fun loadExtensionsCount() {
		if (!settings.isTachiyomiExtensionsEnabled) {
			_extensionsCount.value = 0
			return
		}

		launchLoadingJob(Dispatchers.Default) {
			val extensions = extensionRepository.getEnabledExtensions()
			_extensionsCount.value = extensions.size
		}
	}
}
