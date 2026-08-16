package io.github.landwarderer.futon.settings.sources.extension

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.list.ui.model.ListHeader
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.mihon.extensions.install.ExtensionInstallDownloadState
import io.github.landwarderer.futon.mihon.extensions.install.ExtensionInstallService
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepo
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.futon.mihon.extensions.repo.RepoAvailableExtension
import io.github.landwarderer.futon.mihon.model.MihonLoadResult
import javax.inject.Inject

@HiltViewModel
class ExtensionDownloaderViewModel @Inject constructor(
    private val app: Application,
    private val repoRepository: ExternalExtensionRepoRepository,
    private val extensionManager: MihonExtensionManager,
    private val installService: ExtensionInstallService,
) : BaseViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val catalogExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
    private val searchQuery = MutableStateFlow("")

    private val _intentAction = MutableEventFlow<android.content.Intent>()
    val intentAction = _intentAction

    private val _onRepoEvent = MutableEventFlow<RepoEvent>()
    val onRepoEvent = _onRepoEvent

    val repos: StateFlow<List<ExternalExtensionRepo>> = repoRepository
        .observeByType(ExternalExtensionType.MIHON)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            Log.d("ExtensionDownloaderViewModel", "fetching extensions")
            catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
        }
        refresh()
    }

    private data class RepoCatalogQuery(
        val repos: List<ExternalExtensionRepo>,
        val available: List<RepoAvailableExtension>,
        val query: String,
    )

    private val repoCatalogQueryFlow = combine(
        repos,
        catalogExtensions,
        searchQuery,
    ) { repoList, available, query ->
        RepoCatalogQuery(repoList, available, query)
    }

    val state: StateFlow<ExtensionDownloaderState> = combine(
        repoCatalogQueryFlow,
        extensionManager.installedExtensions,
        installService.downloadStates,
        refreshing,
    ) { (repoList, available, query), installed, downloads, isRefreshing ->
        val filteredExtensions = if (query.isNotEmpty()) {
            available.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            available
        }

        val extensionItems = filteredExtensions.map { extension ->
            val installedExtension = installed.find { it.pkgName == extension.pkgName }
            ExtensionItem(
                available = extension,
                installed = installedExtension,
                downloadState = downloads[extension.pkgName],
            )
        }

        val installedItems = extensionItems
            .filter { it.isInstalled }
            .sortedWith(
                compareByDescending<ExtensionItem> { it.hasUpdate }
                    .thenBy { it.available.name.lowercase() }
            )

        val notInstalledItems = extensionItems
            .filterNot { it.isInstalled }
            .sortedWith(
                compareBy<ExtensionItem> { it.available.lang }
                    .thenBy { it.available.name.lowercase() }
            )

        val items = buildList {
            if (query.isEmpty()) {
                add(RepoHeaderItem(hasRepos = repoList.isNotEmpty()))
                for (repo in repoList) {
                    add(RepoItem(repo))
                }
                if (installedItems.isNotEmpty()) {
                    add(ListHeader(R.string.installed_extensions_header))
                    addAll(installedItems)
                }
                if (notInstalledItems.isNotEmpty()) {
                    if (installedItems.isNotEmpty()) {
                        add(ListHeader(R.string.available_extensions_header))
                    }
                    addAll(notInstalledItems)
                }
            } else {
                // When searching, pin installed matches to top
                addAll(installedItems)
                addAll(notInstalledItems)
            }
        }

        ExtensionDownloaderState(
            items = items,
            isLoading = isRefreshing,
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

    fun addRepo(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = repoRepository.addRepo(ExternalExtensionType.MIHON, cleanUrl)) {
                is ExternalExtensionRepoRepository.AddRepoResult.Success -> {
                    _onRepoEvent.call(RepoEvent.Success(result.repo))
                    refresh()
                }
                is ExternalExtensionRepoRepository.AddRepoResult.DuplicateFingerprint -> {
                    _onRepoEvent.call(RepoEvent.Error(app.getString(R.string.repo_already_exists)))
                }
                is ExternalExtensionRepoRepository.AddRepoResult.FetchFailed -> {
                    _onRepoEvent.call(
                        RepoEvent.Error(
                            app.getString(
                                R.string.failed_to_add_repo,
                                result.error.getDisplayMessage(app.resources),
                            )
                        )
                    )
                }
                ExternalExtensionRepoRepository.AddRepoResult.InvalidUrl -> {
                    _onRepoEvent.call(RepoEvent.Error(app.getString(R.string.invalid_repo_url)))
                }
                ExternalExtensionRepoRepository.AddRepoResult.RepoAlreadyExists -> {
                    _onRepoEvent.call(RepoEvent.Error(app.getString(R.string.repo_already_exists)))
                }
            }
        }
    }

    fun deleteRepo(repo: ExternalExtensionRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            repoRepository.delete(repo)
            refresh()
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

sealed interface RepoEvent {
    data class Success(val repo: ExternalExtensionRepo) : RepoEvent
    data class Error(val message: String) : RepoEvent
}

data class ExtensionDownloaderState(
    val items: List<ListModel> = emptyList(),
    val isLoading: Boolean = false,
)

data class RepoHeaderItem(
    val hasRepos: Boolean,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean = other is RepoHeaderItem
}

data class RepoItem(
    val repo: ExternalExtensionRepo,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean = other is RepoItem && repo.baseUrl == other.repo.baseUrl
}

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
