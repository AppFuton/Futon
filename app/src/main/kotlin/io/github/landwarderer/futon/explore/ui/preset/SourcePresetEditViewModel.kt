package io.github.landwarderer.futon.explore.ui.preset

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.data.SourcePreset
import io.github.landwarderer.futon.explore.data.SourcePresetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class SourcePresetEditViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val presetsRepository: SourcePresetsRepository,
	private val sourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {

	private val presetId = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	val onSaved = MutableEventFlow<Unit>()
	val preset = MutableStateFlow<SourcePreset?>(null)

	val allLocales: Set<String> = sourcesRepository.getAllSourceLanguages()

	init {
		launchLoadingJob(Dispatchers.IO) {
			preset.value = if (presetId != NO_ID) {
				presetsRepository.getById(presetId)
			} else {
				null
			}
		}
	}

	fun save(title: String, selectedLanguages: Set<String>) {
		launchLoadingJob(Dispatchers.IO) {
			check(title.isNotEmpty())
			if (presetId == NO_ID) {
				val initialSources = sourcesRepository.getSourceNamesForLanguages(selectedLanguages)
				presetsRepository.createPreset(title, selectedLanguages, initialSources)
			} else {
				presetsRepository.updatePreset(presetId, title, selectedLanguages)
			}
			onSaved.call(Unit)
		}
	}

	companion object {
		const val NO_ID = -1L
	}
}
