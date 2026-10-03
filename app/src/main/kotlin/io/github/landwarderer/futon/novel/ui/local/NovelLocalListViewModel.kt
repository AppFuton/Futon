package io.github.landwarderer.futon.novel.ui.local

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.model.LocalNovelSource
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.domain.ExploreRepository
import io.github.landwarderer.futon.filter.ui.FilterCoordinator
import io.github.landwarderer.futon.list.domain.MangaListMapper
import io.github.landwarderer.futon.novel.data.NovelStorageManager
import io.github.landwarderer.futon.local.data.LocalStorageChanges
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.remotelist.ui.RemoteListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
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

	fun importFiles(uris: List<Uri>) {
		launchJob(Dispatchers.IO) {
			for (uri in uris) {
				novelStorageManager.importNovel(uri)
			}
			onRefresh()
		}
	}
}
