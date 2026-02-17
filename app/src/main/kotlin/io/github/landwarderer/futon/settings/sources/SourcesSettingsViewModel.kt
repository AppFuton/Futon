package io.github.landwarderer.futon.settings.sources

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import javax.inject.Inject

@HiltViewModel
class SourcesSettingsViewModel @Inject constructor(
	sourcesRepository: MangaSourcesRepository,
	@ApplicationContext private val context: Context,
) : BaseViewModel() {

	private val linksHandlerActivity = ComponentName(context, "io.github.landwarderer.futon.details.ui.DetailsByLinkActivity")

	val enabledSourcesCount = sourcesRepository.observeEnabledSourcesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, -1)

	val availableSourcesCount = sourcesRepository.observeAvailableSourcesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, -1)

	val isLinksEnabled = MutableStateFlow(isLinksEnabled())

	fun setLinksEnabled(isEnabled: Boolean) {
		context.packageManager.setComponentEnabledSetting(
			linksHandlerActivity,
			if (isEnabled) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED,
			PackageManager.DONT_KILL_APP,
		)
		isLinksEnabled.value = isLinksEnabled()
	}

	private fun isLinksEnabled(): Boolean {
		val state = context.packageManager.getComponentEnabledSetting(linksHandlerActivity)
		return state == COMPONENT_ENABLED_STATE_ENABLED || state == COMPONENT_ENABLED_STATE_DEFAULT
	}
}
