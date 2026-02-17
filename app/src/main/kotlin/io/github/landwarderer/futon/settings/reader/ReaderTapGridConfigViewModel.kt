package io.github.landwarderer.futon.settings.reader

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.reader.data.TapGridSettings
import io.github.landwarderer.futon.reader.domain.TapGridArea
import io.github.landwarderer.futon.reader.ui.tapgrid.TapAction
import java.util.EnumMap
import javax.inject.Inject

@HiltViewModel
class ReaderTapGridConfigViewModel @Inject constructor(
	private val tapGridSettings: TapGridSettings,
) : BaseViewModel() {

	val content = tapGridSettings.observeChanges()
		.onStart { emit(null) }
		.map { getData() }
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyMap())

	fun reset() {
		launchJob(Dispatchers.IO) {
			tapGridSettings.reset()
		}
	}

	fun disableAll() {
		launchJob(Dispatchers.IO) {
			tapGridSettings.disableAll()
		}
	}

	fun setTapAction(area: TapGridArea, isLongTap: Boolean, action: TapAction?) {
		launchJob(Dispatchers.IO) {
			tapGridSettings.setTapAction(area, isLongTap, action)
		}
	}

	private fun getData(): Map<TapGridArea, TapActions> {
		val map = EnumMap<TapGridArea, TapActions>(TapGridArea::class.java)
		for (area in TapGridArea.entries) {
			map[area] = TapActions(
				tapAction = tapGridSettings.getTapAction(area, isLongTap = false),
				longTapAction = tapGridSettings.getTapAction(area, isLongTap = true),
			)
		}
		return map
	}

	data class TapActions(
		val tapAction: TapAction?,
		val longTapAction: TapAction?,
	)
}
