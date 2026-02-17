package io.github.landwarderer.futon.settings.storage.directories

import android.net.Uri
import android.os.StatFs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.computeSize
import io.github.landwarderer.futon.core.util.ext.isReadable
import io.github.landwarderer.futon.core.util.ext.isWriteable
import io.github.landwarderer.futon.local.data.LocalStorageManager
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MangaDirectoriesViewModel @Inject constructor(
    private val storageManager: LocalStorageManager,
    private val settings: AppSettings,
) : BaseViewModel() {

    val items = MutableStateFlow(emptyList<DirectoryConfigModel>())
    private var loadingJob: Job? = null

    init {
        loadList()
    }

    fun updateList() {
        loadList()
    }

    fun onCustomDirectoryPicked(uri: Uri) {
        launchLoadingJob(Dispatchers.IO) {
            loadingJob?.cancelAndJoin()
            storageManager.takePermissions(uri)
            val dir = storageManager.resolveUri(uri)
            if (!dir.canRead()) {
                throw AccessDeniedException(dir)
            }
            if (dir !in storageManager.getApplicationStorageDirs()) {
                settings.userSpecifiedMangaDirectories += dir
                loadList()
            }
        }
    }

    fun onRemoveClick(directory: File) {
        settings.userSpecifiedMangaDirectories -= directory
        if (settings.mangaStorageDir == directory) {
            settings.mangaStorageDir = null
        }
        loadList()
    }

    private fun loadList() {
        val prevJob = loadingJob
        loadingJob = launchJob(Dispatchers.IO) {
            prevJob?.cancelAndJoin()
            val downloadDir = storageManager.getDefaultWriteableDir()
            val applicationDirs = storageManager.getApplicationStorageDirs()
            val customDirs = settings.userSpecifiedMangaDirectories - applicationDirs
            items.value = buildList(applicationDirs.size + customDirs.size) {
                applicationDirs.mapTo(this) { dir ->
                    dir.toDirectoryModel(
                        isDefault = dir == downloadDir,
                        isAppPrivate = true,
                    )
                }
                customDirs.mapTo(this) { dir ->
                    dir.toDirectoryModel(
                        isDefault = dir == downloadDir,
                        isAppPrivate = false,
                    )
                }
            }
        }
    }

    private suspend fun File.toDirectoryModel(
        isDefault: Boolean,
        isAppPrivate: Boolean,
    ) = DirectoryConfigModel(
        title = storageManager.getDirectoryDisplayName(this, isFullPath = false),
        path = this,
        isDefault = isDefault,
        isAccessible = isReadable() && isWriteable(),
        isAppPrivate = isAppPrivate,
        size = computeSize(),
        available = StatFs(this.absolutePath).availableBytes,
    )
}
