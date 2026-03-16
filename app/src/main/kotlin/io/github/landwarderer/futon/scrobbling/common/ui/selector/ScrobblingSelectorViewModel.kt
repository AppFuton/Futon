package io.github.landwarderer.futon.scrobbling.common.ui.selector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView.NO_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.core.util.ext.requireValue
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.list.domain.ReadingProgress
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingFooter
import io.github.landwarderer.futon.list.ui.model.LoadingState
import io.github.landwarderer.futon.parsers.util.ifZero
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.scrobbling.common.domain.Scrobbler
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblerManga
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblingStatus
import io.github.landwarderer.futon.scrobbling.common.ui.selector.model.ScrobblerHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class ScrobblingSelectorViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	scrobblersProvider: Provider<Set<@JvmSuppressWildcards Scrobbler>>,
	private val historyRepository: HistoryRepository,
) : BaseViewModel() {

	val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	private val scrobblers by lazy { scrobblersProvider.get() }
	val availableScrobblers = scrobblers.filter { it.isEnabled }

	val selectedScrobblerIndex = MutableStateFlow(0)

	private val scrobblerMangaList = MutableStateFlow<List<ScrobblerManga>>(emptyList())
	private val hasNextPage = MutableStateFlow(true)
	private val listError = MutableStateFlow<Throwable?>(null)
	private var loadingJob: Job? = null
	private var doneJob: Job? = null
	private var initJob: Job? = null

	private val currentScrobbler: Scrobbler
		get() = availableScrobblers[selectedScrobblerIndex.requireValue()]

	val content: StateFlow<List<ListModel>> = combine(
		scrobblerMangaList,
		listError,
		hasNextPage,
	) { list, error, isHasNextPage ->
		if (list.isNotEmpty()) {
			if (isHasNextPage) {
				list + LoadingFooter()
			} else {
				list
			}
		} else {
			listOf(
				when {
					error != null -> errorHint(error)
					isHasNextPage -> LoadingFooter()
					else -> emptyResultsHint()
				},
			)
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	val selectedItemId = MutableStateFlow(NO_ID)
	val onClose = MutableEventFlow<Unit>()
	private val searchQuery = MutableStateFlow(manga.title)

	val isEmpty: Boolean
		get() = scrobblerMangaList.value.isEmpty()

	init {
		initialize()
	}

	fun search(query: String) {
		loadingJob?.cancel()
		searchQuery.value = query
		loadList(append = false)
	}

	fun selectItem(id: Long) {
		if (doneJob?.isActive == true) {
			return
		}
		selectedItemId.value = id
	}

	fun loadNextPage() {
		if (scrobblerMangaList.value.isNotEmpty() && hasNextPage.value) {
			loadList(append = true)
		}
	}

	fun retry() {
		loadingJob?.cancel()
		hasNextPage.value = true
		scrobblerMangaList.value = emptyList()
		loadList(append = false)
	}

	private fun loadList(append: Boolean) {
		if (loadingJob?.isActive == true) {
			return
		}
		loadingJob = launchJob(Dispatchers.IO) {
			listError.value = null
			val offset = if (append) scrobblerMangaList.value.size else 0
			runCatchingCancellable {
				currentScrobbler.findManga(checkNotNull(searchQuery.value), offset)
			}.onSuccess { list ->
				val newList = (if (append) {
					scrobblerMangaList.value + list
				} else {
					list
				}).distinctBy { x -> x.id }
				val changed = newList != scrobblerMangaList.value
				scrobblerMangaList.value = newList
				hasNextPage.value = changed && newList.isNotEmpty()
			}.onFailure { error ->
				error.printStackTraceDebug("ScrobblingSelectorViewModel::loadList")
				hasNextPage.value = false
				listError.value = error
			}
		}
	}

	fun onDoneClick() {
		if (doneJob?.isActive == true) {
			return
		}
		val targetId = selectedItemId.value
		if (targetId == NO_ID) {
			onClose.call(Unit)
		}
		doneJob = launchLoadingJob(Dispatchers.IO) {
			val prevInfo = currentScrobbler.getScrobblingInfoOrNull(manga.id)
			currentScrobbler.linkManga(manga.id, targetId)
			val history = historyRepository.getOne(manga)
			currentScrobbler.updateScrobblingInfo(
				mangaId = manga.id,
				rating = prevInfo?.rating ?: 0f,
				status = prevInfo?.status ?: when {
					history == null -> ScrobblingStatus.PLANNED
					ReadingProgress.isCompleted(history.percent) -> ScrobblingStatus.COMPLETED
					else -> ScrobblingStatus.READING
				},
				comment = prevInfo?.comment,
			)
			if (history != null) {
				currentScrobbler.scrobble(
					manga = manga,
					chapterId = history.chapterId,
				)
			}
			onClose.call(Unit)
		}
	}

	fun setScrobblerIndex(index: Int) {
		if (index == selectedScrobblerIndex.value || index !in availableScrobblers.indices) return
		selectedScrobblerIndex.value = index
		initialize()
	}

	private fun initialize() {
		initJob?.cancel()
		loadingJob?.cancel()
		hasNextPage.value = true
		scrobblerMangaList.value = emptyList()
		initJob = launchJob(Dispatchers.IO) {
			try {
				val info = currentScrobbler.getScrobblingInfoOrNull(manga.id)
				if (info != null) {
					selectedItemId.value = info.targetId
				}
			} finally {
				loadList(append = false)
			}
		}
	}

	private fun emptyResultsHint() = ScrobblerHint(
		icon = R.drawable.ic_empty_history,
		textPrimary = R.string.nothing_found,
		textSecondary = R.string.text_search_holder_secondary,
		error = null,
		actionStringRes = R.string.search,
	)

	private fun errorHint(e: Throwable): ScrobblerHint {
		val resolveAction = ExceptionResolver.getResolveStringId(e)
		return ScrobblerHint(
			icon = R.drawable.ic_error_large,
			textPrimary = R.string.error_occurred,
			error = e,
			textSecondary = if (resolveAction == 0) 0 else R.string.try_again,
			actionStringRes = resolveAction.ifZero { R.string.try_again },
		)
	}
}
