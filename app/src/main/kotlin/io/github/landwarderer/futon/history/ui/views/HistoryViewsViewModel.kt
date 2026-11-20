package io.github.landwarderer.futon.history.ui.views

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.model.isNovel
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.history.domain.HistoryViewMode
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import io.github.landwarderer.futon.history.domain.model.ReadingSession
import io.github.landwarderer.futon.list.domain.ListSortOrder
import io.github.landwarderer.futon.stats.data.StatsRepository

import javax.inject.Inject

private const val MAX_ENTRIES = 2000

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewsViewModel @Inject constructor(
	historyRepository: HistoryRepository,
	statsRepository: StatsRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	val mode: StateFlow<HistoryViewMode> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_HISTORY_VIEW_MODE,
		valueProducer = { historyViewMode },
	)

	val isStatsEnabled: StateFlow<Boolean> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_STATS_ENABLED,
		valueProducer = { isStatsEnabled },
	)

	val isMinimalTheme: StateFlow<Boolean> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_MINIMAL_THEME,
		valueProducer = { isMinimalThemeEnabled },
	)

	/** Null until the first load, so the views do not flash their empty text. Prepared on a background thread. */
	val state: StateFlow<HistoryViewsState?> = isStatsEnabled.flatMapLatest { isStatsOn ->
		combine(
			historyRepository.observeAllWithHistory(ListSortOrder.LAST_READ, emptySet(), MAX_ENTRIES),
			settings.observeAppMode(),
			// Summed per manga and day by the database, rather than loading every session.
			if (isStatsOn) statsRepository.observeDailyTotals() else flowOf(emptyList()),
		) { history, appMode, stats ->
			val isNovel = appMode.isNovel
			val hideNsfw = settings.isNsfwContentDisabled
			val sessions = stats.groupBy(
				keySelector = { it.mangaId },
				valueTransform = { ReadingSession(it.startedAt, it.duration, it.pages) },
			)
			val entries = history.filter { (manga, _) ->
				manga.isNovel == isNovel && !(hideNsfw && manga.isNsfw())
			}.map { (manga, item) ->
				HistoryEntry(
					manga = manga,
					percent = item.percent,
					updatedAt = item.updatedAt.toEpochMilli(),
					chaptersCount = item.chaptersCount,
					sessions = sessions[manga.id].orEmpty(),
				)
			}
			HistoryViewsState(entries, isStatsOn)
		}
	}.flowOn(Dispatchers.Default)
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), null)

	fun setMode(value: HistoryViewMode) {
		settings.historyViewMode = value
	}
}
