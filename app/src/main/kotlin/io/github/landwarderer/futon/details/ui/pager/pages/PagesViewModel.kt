package io.github.landwarderer.futon.details.ui.pager.pages

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.firstNotNull
import io.github.landwarderer.futon.core.util.ext.requireValue
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.list.ui.model.ListHeader
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.reader.domain.ChaptersLoader
import io.github.landwarderer.futon.reader.ui.PageSaveHelper
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import javax.inject.Inject

@HiltViewModel
class PagesViewModel @Inject constructor(
	private val chaptersLoader: ChaptersLoader,
	settings: AppSettings,
) : BaseViewModel() {

	private var loadingJob: Job? = null
	private var loadingPrevJob: Job? = null
	private var loadingNextJob: Job? = null

	private val state = MutableStateFlow<State?>(null)
	val thumbnails = MutableStateFlow<List<ListModel>>(emptyList())
	val isLoadingUp = MutableStateFlow(false)
	val isLoadingDown = MutableStateFlow(false)
	val onPageSaved = MutableEventFlow<Collection<Uri>>()

	val gridScale = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_GRID_SIZE_PAGES,
		valueProducer = { gridSizePages / 100f },
	)

	init {
		launchJob(Dispatchers.IO) {
			state.filterNotNull()
				.collect {
					val prevJob = loadingJob
					loadingJob = launchLoadingJob(Dispatchers.IO) {
						prevJob?.cancelAndJoin()
						doInit(it)
					}
				}
		}
	}

	fun updateState(newState: State?) {
		if (newState != null) {
			state.value = newState
		}
	}

	fun loadPrevChapter() {
		if (loadingJob?.isActive == true || loadingPrevJob?.isActive == true) {
			return
		}
		loadingPrevJob = loadPrevNextChapter(isNext = false)
	}

	fun loadNextChapter() {
		if (loadingJob?.isActive == true || loadingNextJob?.isActive == true) {
			return
		}
		loadingNextJob = loadPrevNextChapter(isNext = true)
	}

	fun savePages(
		pageSaveHelper: PageSaveHelper,
		pages: Set<ReaderPage>,
	) {
		launchLoadingJob(Dispatchers.IO) {
			val manga = state.requireValue().details.toManga()
			val tasks = pages.map {
				PageSaveHelper.Task(
					manga = manga,
					chapterId = it.chapterId,
					pageNumber = it.index + 1,
					page = it.toMangaPage(),
				)
			}
			val dest = pageSaveHelper.save(tasks)
			onPageSaved.call(dest)
		}
	}

	private suspend fun doInit(state: State) {
		chaptersLoader.init(state.details)
		val initialChapterId = state.readerState?.chapterId?.takeIf {
			chaptersLoader.peekChapter(it) != null
		} ?: state.details.allChapters.firstOrNull()?.id ?: return
		if (!chaptersLoader.hasPages(initialChapterId)) {
			var hasPages = chaptersLoader.loadSingleChapter(initialChapterId)
			while (!hasPages) {
				if (chaptersLoader.loadPrevNextChapter(state.details, initialChapterId, isNext = true)) {
					hasPages = chaptersLoader.snapshot().isNotEmpty()
				} else {
					break
				}
			}
		}
		updateList(state.readerState)
	}

	private fun loadPrevNextChapter(isNext: Boolean): Job = launchJob(Dispatchers.IO) {
		val indicator = if (isNext) isLoadingDown else isLoadingUp
		indicator.value = true
		try {
			val currentState = state.firstNotNull()
			val currentId = (if (isNext) chaptersLoader.last() else chaptersLoader.first()).chapterId
			chaptersLoader.loadPrevNextChapter(currentState.details, currentId, isNext)
			updateList(currentState.readerState)
		} finally {
			indicator.value = false
		}
	}

	private fun updateList(readerState: ReaderState?) {
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
					isCurrent = readerState?.let {
						page.chapterId == it.chapterId && page.index == it.page
					} == true,
					page = page,
				)
			}
		}
		thumbnails.value = pages
	}

	data class State(
		val details: MangaDetails,
		val readerState: ReaderState?,
		val branch: String?
	)
}
