package io.github.landwarderer.futon.settings.sources.extension

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.mihon.extensions.install.ExtensionInstallDownloadState
import io.github.landwarderer.futon.mihon.extensions.install.ExtensionInstallService
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.futon.mihon.extensions.repo.RepoAvailableExtension
import io.github.landwarderer.futon.mihon.model.MihonLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionDownloaderViewModel @Inject constructor(
    private val repoRepository: ExternalExtensionRepoRepository,
    private val extensionManager: MihonExtensionManager,
    private val installService: ExtensionInstallService,
) : BaseViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val catalogExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
    private val searchQuery = MutableStateFlow("")

    private val _intentAction = MutableEventFlow<android.content.Intent>()
    val intentAction = _intentAction

    init {
        viewModelScope.launch {
            Log.d("ExtensionDownloaderViewModel", "fetching extensions")
            catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
        }
        refresh()
    }

    val state: StateFlow<ExtensionDownloaderState> = combine(
        catalogExtensions,
        extensionManager.installedExtensions,
        installService.downloadStates,
        refreshing,
        searchQuery
    ) { available, installed, downloads, isRefreshing, query ->
        val filteredExtensions = if (query.isNotEmpty()) {
            available.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            available
        }
        val items = filteredExtensions.map { extension ->
            val installedExtension = installed.find { it.pkgName == extension.pkgName }
            ExtensionItem(
                available = extension,
                installed = installedExtension,
                downloadState = downloads[extension.pkgName]
            )
        }
        ExtensionDownloaderState(
            items = items,
            isLoading = isRefreshing
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ExtensionDownloaderState())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshing.value = true
            try {
                repoRepository.refresh(ExternalExtensionType.MIHON)
                catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun installExtension(extension: RepoAvailableExtension) {
        viewModelScope.launch {
            val intent = installService.createInstallIntent(extension)
            if (intent != null) {
                _intentAction.call(intent)
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        val intent = installService.getUninstallIntent(pkgName)
        _intentAction.call(intent)
    }

    fun cancelDownload(pkgName: String) {
        installService.cancelDownload(pkgName)
    }

    fun performSearch(query: String?) {
        searchQuery.value = query?.trim().orEmpty()
    }
}

data class ExtensionDownloaderState(
    val items: List<ExtensionItem> = emptyList(),
    val isLoading: Boolean = false,
)

data class ExtensionItem(
    val available: RepoAvailableExtension,
    val installed: MihonLoadResult.Success?,
    val downloadState: ExtensionInstallDownloadState?,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean {
        return other is ExtensionItem && available.pkgName == other.available.pkgName
    }
    val isInstalled: Boolean get() = installed != null
    val hasUpdate: Boolean get() = installed != null && available.versionCode > installed.versionCode
}
