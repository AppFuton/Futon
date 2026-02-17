package io.github.landwarderer.futon.picker.ui.page

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.nav.MangaIntent
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.firstNotNull
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.domain.DetailsLoadUseCase
import io.github.landwarderer.futon.details.ui.pager.pages.PageThumbnail
import io.github.landwarderer.futon.list.ui.model.ListHeader
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.reader.domain.ChaptersLoader
import javax.inject.Inject

@HiltViewModel
class PagePickerViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val chaptersLoader: ChaptersLoader,
	private val detailsLoadUseCase: DetailsLoadUseCase,
	settings: AppSettings,
) : BaseViewModel() {

	private val intent = MangaIntent(savedStateHandle)

	private var loadingJob: Job? = null
	private var loadingNextJob: Job? = null

	val thumbnails = MutableStateFlow<List<ListModel>>(emptyList())
	val isLoadingDown = MutableStateFlow(false)
	val manga = MutableStateFlow(intent.manga?.let { MangaDetails(it) })

	val isNoChapters = manga.map {
		it != null && it.isLoaded && it.allChapters.isEmpty()
	}

	val gridScale = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_GRID_SIZE_PAGES,
		valueProducer = { gridSizePages / 100f },
	)

	init {
		loadingJob = launchLoadingJob(Dispatchers.IO) {
			doInit()
		}
	}

	private suspend fun doInit() {
		val details = detailsLoadUseCase.invoke(intent, force = false)
			.onEach { manga.value = it }
			.first { x -> x.isLoaded }
		chaptersLoader.init(details)
		val initialChapterId = details.allChapters.firstOrNull()?.id ?: return
		if (!chaptersLoader.hasPages(initialChapterId)) {
			chaptersLoader.loadSingleChapter(initialChapterId)
		}
		updateList()
	}

	fun loadNextChapter() {
		if (loadingJob?.isActive == true || loadingNextJob?.isActive == true) {
			return
		}
		loadingNextJob = launchJob(Dispatchers.IO) {
			isLoadingDown.value = true
			try {
				val currentId = chaptersLoader.last().chapterId
				chaptersLoader.loadPrevNextChapter(manga.firstNotNull(), currentId, isNext = true)
				updateList()
			} finally {
				isLoadingDown.value = false
			}
		}
	}

	private fun updateList() {
		val snapshot = chaptersLoader.snapshot()
		val pages = buildList(snapshot.size + chaptersLoader.size + 2) {
			var previousChapterId = 0L
			for (page in snapshot) {
				if (page.chapterId != previousChapterId) {
					chaptersLoader.peekChapter(page.chapterId)?.let {
						add(ListHeader(it))
					}
					previousChapterId = page.chapterId
				}
				this += PageThumbnail(
					isCurrent = false,
					page = page,
				)
			}
		}
		thumbnails.value = pages
	}
}
