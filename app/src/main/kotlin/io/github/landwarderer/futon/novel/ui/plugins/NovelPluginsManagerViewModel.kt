package io.github.landwarderer.futon.novel.ui.plugins

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.repository.LnPluginManager
import javax.inject.Inject

data class NovelPluginsState(
    val isLoading: Boolean = false,
    val items: List<LnPlugin> = emptyList(),
    val installingIds: Set<String> = emptySet(),
    val error: Throwable? = null,
)

sealed interface NovelPluginEvent {
    data class ShowMessage(val messageRes: Int, val formatArg: String? = null) : NovelPluginEvent
    data class ShowError(val messageRes: Int) : NovelPluginEvent
}

@HiltViewModel
class NovelPluginsManagerViewModel @Inject constructor(
    private val pluginManager: LnPluginManager,
) : BaseViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow(NovelPluginsState(isLoading = true))
    val state: StateFlow<NovelPluginsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NovelPluginEvent>()
    val events: SharedFlow<NovelPluginEvent> = _events.asSharedFlow()

    private var allPlugins: List<LnPlugin> = emptyList()
    private var currentFilter: String = ""

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        launchJob(ioDispatcher) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                allPlugins = pluginManager.fetchAvailablePlugins()
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTraceDebug()
                val installed = pluginManager.getInstalledPlugins()
                allPlugins = installed
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = installed,
                    error = if (installed.isEmpty()) e else null,
                )
            }
        }
    }

    fun filter(query: String) {
        currentFilter = query.trim()
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentFilter.isEmpty()) {
            allPlugins
        } else {
            allPlugins.filter {
                it.name.contains(currentFilter, ignoreCase = true) ||
                    it.lang.contains(currentFilter, ignoreCase = true)
            }
        }
        val sorted = filtered.sortedWith(
            compareByDescending<LnPlugin> { it.hasUpdate }
                .thenByDescending { it.isInstalled }
                .thenBy { it.name.lowercase() },
        )
        _state.value = _state.value.copy(
            isLoading = false,
            items = sorted,
            error = null,
        )
    }

    fun installPlugin(plugin: LnPlugin) {
        launchJob(ioDispatcher) {
            val installing = _state.value.installingIds + plugin.id
            _state.value = _state.value.copy(installingIds = installing)
            try {
                val success = pluginManager.installPlugin(plugin)
                if (success) {
                    _events.emit(NovelPluginEvent.ShowMessage(R.string.plugin_install_success, plugin.name))
                    loadPlugins()
                } else {
                    _events.emit(NovelPluginEvent.ShowError(R.string.plugin_install_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTraceDebug()
                _events.emit(NovelPluginEvent.ShowError(R.string.plugin_install_failed))
            } finally {
                _state.value = _state.value.copy(installingIds = _state.value.installingIds - plugin.id)
            }
        }
    }

    fun uninstallPlugin(plugin: LnPlugin) {
        launchJob(ioDispatcher) {
            val installing = _state.value.installingIds + plugin.id
            _state.value = _state.value.copy(installingIds = installing)
            try {
                val success = pluginManager.uninstallPlugin(plugin.id)
                if (success) {
                    _events.emit(NovelPluginEvent.ShowMessage(R.string.plugin_uninstall_success, plugin.name))
                    loadPlugins()
                } else {
                    _events.emit(NovelPluginEvent.ShowError(R.string.plugin_uninstall_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTraceDebug()
                _events.emit(NovelPluginEvent.ShowError(R.string.plugin_uninstall_failed))
            } finally {
                _state.value = _state.value.copy(installingIds = _state.value.installingIds - plugin.id)
            }
        }
    }
}

