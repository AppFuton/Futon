package io.github.landwarderer.futon.settings.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtension
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstalledExtensionsViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val extensionRepository: TachiyomiExtensionRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val _installedExtensions = MutableStateFlow<List<ListModel>>(listOf(LoadingState))
	val installedExtensions: StateFlow<List<ListModel>> = _installedExtensions.asStateFlow()

	init {
		if (settings.isTachiyomiExtensionsEnabled) {
			observeInstalledExtensions()
		} else {
			_installedExtensions.value = emptyList()
		}
	}

	private fun observeInstalledExtensions() {
		viewModelScope.launch(Dispatchers.Default) {
			extensionRepository.observeAllExtensions().collectLatest { extensions ->
				_installedExtensions.value = if (extensions.isEmpty()) {
					emptyList()
				} else {
					extensions.map { extension ->
						InstalledExtensionItem(extension = extension)
					}
				}
			}
		}
	}

	fun toggleExtensionEnabled(pkgName: String, currentlyEnabled: Boolean) {
		launchLoadingJob(Dispatchers.Default) {
			extensionRepository.setExtensionEnabled(pkgName, !currentlyEnabled)
		}
	}

	fun uninstallExtension(pkgName: String) {
		val intent = Intent(Intent.ACTION_DELETE).apply {
			data = Uri.parse("package:$pkgName")
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		context.startActivity(intent)
	}
}

data class InstalledExtensionItem(
	val extension: TachiyomiExtension,
) : ListModel {
	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is InstalledExtensionItem && other.extension.pkgName == extension.pkgName
	}
}
