package io.github.landwarderer.futon.details.ui.related

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.details.domain.RelatedMangaUseCase
import io.github.landwarderer.futon.list.domain.MangaListMapper
import io.github.landwarderer.futon.list.ui.MangaListViewModel
import io.github.landwarderer.futon.list.ui.model.EmptyState
import io.github.landwarderer.futon.list.ui.model.LoadingState
import io.github.landwarderer.futon.list.ui.model.toErrorState
import io.github.landwarderer.futon.local.data.LocalStorageChanges
import io.github.landwarderer.futon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class RelatedListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	settings: AppSettings,
	private val relatedMangaUseCase: RelatedMangaUseCase,
	private val mangaListMapper: MangaListMapper,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges) {

	private val seed = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga
	private val mangaList = MutableStateFlow<List<Manga>?>(null)
	private val listError = MutableStateFlow<Throwable?>(null)
	private var loadingJob: Job? = null

	override val content = combine(
		mangaList,
		observeListModeWithTriggers(),
		listError,
	) { list, mode, error ->
		when {
			list.isNullOrEmpty() && error != null -> listOf(error.toErrorState(canRetry = true))
			list == null -> listOf(LoadingState())
			list.isEmpty() -> listOf(createEmptyState())
			else -> mangaListMapper.toListModelList(list, mode)
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState()))

	init {
		loadList()
	}

	override fun onRefresh() {
		loadList()
	}

	override fun onRetry() {
		loadList()
	}

	private fun loadList(): Job {
		loadingJob?.let {
			if (it.isActive) return it
		}
		return launchLoadingJob(Dispatchers.IO) {
			try {
				listError.value = null
				// Same path as the details screen row: the source's own related list, then tag matches.
				mangaList.value = relatedMangaUseCase(seed).orEmpty()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug("RelatedListViewModel::loadList")
				listError.value = e
				if (!mangaList.value.isNullOrEmpty()) {
					errorEvent.call(e)
				}
			}
		}.also { loadingJob = it }
	}

	private fun createEmptyState() = EmptyState(
		icon = R.drawable.ic_empty_common,
		textPrimary = R.string.nothing_found,
		textSecondary = 0,
		actionStringRes = 0,
	)
}

