package io.github.landwarderer.futon.settings.extensions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class ExtensionsSettingsViewModel @Inject constructor(
	private val extensionRepository: TachiyomiExtensionRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	val extensionsCount: StateFlow<Int> = extensionRepository.observeExtensionCount()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0)

	fun onExtensionsEnabledChanged() {
	}
}
