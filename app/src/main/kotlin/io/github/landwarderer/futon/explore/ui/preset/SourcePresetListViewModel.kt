package io.github.landwarderer.futon.explore.ui.preset

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.explore.data.SourcePreset
import io.github.landwarderer.futon.explore.data.SourcePresetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class SourcePresetListViewModel @Inject constructor(
	private val presetsRepository: SourcePresetsRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	val presets: StateFlow<List<SourcePreset>> = presetsRepository.observeAll()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	val onPresetDeleted = MutableEventFlow<Unit>()

	val activePresetId: Long
		get() = settings.activeSourcePresetId

	fun setActivePreset(presetId: Long) {
		settings.activeSourcePresetId = presetId
	}

	fun deletePreset(presetId: Long) {
		launchJob(Dispatchers.IO) {
			if (settings.activeSourcePresetId == presetId) {
				settings.activeSourcePresetId = SourcePresetsRepository.NO_PRESET
			}
			presetsRepository.deletePreset(presetId)
			onPresetDeleted.call(Unit)
		}
	}
}
