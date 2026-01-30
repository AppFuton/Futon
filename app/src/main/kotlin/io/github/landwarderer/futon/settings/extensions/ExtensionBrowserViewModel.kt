package io.github.landwarderer.futon.settings.extensions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionIndexParser
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionMetadata
import io.github.landwarderer.futon.core.parser.tachiyomi.TachiyomiExtensionRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionBrowserViewModel @Inject constructor(
	private val indexParser: TachiyomiExtensionIndexParser,
	private val extensionRepository: TachiyomiExtensionRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val _availableExtensions = MutableStateFlow<List<ListModel>>(listOf(LoadingState))
	val availableExtensions: StateFlow<List<ListModel>> = _availableExtensions.asStateFlow()

	private val _searchQuery = MutableStateFlow("")
	val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

	private val _showNsfw = MutableStateFlow(false)
	val showNsfw: StateFlow<Boolean> = _showNsfw.asStateFlow()

	private var allExtensions: List<TachiyomiExtensionMetadata> = emptyList()
	private var installedPackages: Set<String> = emptySet()

	init {
		if (settings.isTachiyomiExtensionsEnabled) {
			loadExtensions()
		} else {
			_availableExtensions.value = emptyList()
		}
	}

	fun loadExtensions() {
		if (!settings.isTachiyomiExtensionsEnabled) {
			_availableExtensions.value = emptyList()
			return
		}

		launchLoadingJob(Dispatchers.Default) {
			_availableExtensions.value = listOf(LoadingState)

			installedPackages = extensionRepository.getAllExtensions()
				.map { it.pkgName }
				.toSet()

			val remote = indexParser.fetchExtensions()
			allExtensions = remote

			applyFilters()
		}
	}

	fun setSearchQuery(query: String) {
		_searchQuery.value = query
		applyFilters()
	}

	fun toggleNsfw() {
		_showNsfw.value = !_showNsfw.value
		applyFilters()
	}

	private fun applyFilters() {
		viewModelScope.launch(Dispatchers.Default) {
			val query = _searchQuery.value.lowercase()
			val showNsfw = _showNsfw.value

			val filtered = allExtensions.filter { ext ->
				val matchesNsfw = showNsfw || !ext.isNsfw
				val matchesSearch = query.isEmpty() ||
					ext.name.lowercase().contains(query) ||
					ext.sources.any { it.name.lowercase().contains(query) }

				matchesNsfw && matchesSearch
			}

			_availableExtensions.value = filtered.map { metadata ->
				ExtensionBrowserItem(
					metadata = metadata,
					isInstalled = installedPackages.contains(metadata.pkgName),
				)
			}
		}
	}
}

data class ExtensionBrowserItem(
	val metadata: TachiyomiExtensionMetadata,
	val isInstalled: Boolean,
) : ListModel {
	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ExtensionBrowserItem && other.metadata.pkgName == metadata.pkgName
	}
}
