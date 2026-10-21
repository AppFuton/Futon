package io.github.landwarderer.futon.novel.ui.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.isFileUri
import io.github.landwarderer.futon.core.util.ext.resolveFile
import io.github.landwarderer.futon.core.util.ext.toFileOrNull
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.domain.ExploreRepository
import io.github.landwarderer.futon.filter.ui.FilterCoordinator
import io.github.landwarderer.futon.list.domain.MangaListMapper
import io.github.landwarderer.futon.local.data.LocalStorageChanges
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.novel.data.NovelStorageManager
import io.github.landwarderer.futon.remotelist.ui.RemoteListViewModel
import javax.inject.Inject

@HiltViewModel
class NovelLocalListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	filterCoordinator: FilterCoordinator,
	settings: AppSettings,
	mangaListMapper: MangaListMapper,
	exploreRepository: ExploreRepository,
	sourcesRepository: MangaSourcesRepository,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
	private val novelStorageManager: NovelStorageManager,
	@ApplicationContext private val context: Context,
) : RemoteListViewModel(
	savedStateHandle = savedStateHandle,
	mangaRepositoryFactory = mangaRepositoryFactory,
	filterCoordinator = filterCoordinator,
	settings = settings,
	mangaListMapper = mangaListMapper,
	exploreRepository = exploreRepository,
	sourcesRepository = sourcesRepository,
	mangaDataRepository = mangaDataRepository,
	localStorageChanges = localStorageChanges,
) {

	fun importFolder(uri: Uri) {
		launchJob(Dispatchers.IO) {
			val dir = (if (uri.isFileUri()) uri.toFileOrNull() else uri.resolveFile(context))
				?: uri.resolveFile(context)
			if (dir != null && (dir.exists() || dir.mkdirs())) {
				novelStorageManager.addNovelDirectory(dir)
			}
			onRefresh()
		}
	}

	fun importFiles(uris: List<Uri>) {
		launchJob(Dispatchers.IO) {
			for (uri in uris) {
				novelStorageManager.importNovel(uri)
			}
			onRefresh()
		}
	}
}
