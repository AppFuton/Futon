package io.github.landwarderer.futon.tracker.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import io.github.landwarderer.futon.core.model.withOverride
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsFlow
import io.github.landwarderer.futon.list.domain.ListFilterOption
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

private const val ROLLING_WINDOW_REFRESH_MS = 600_000L

data class UpdatesShelf(
	val window: UpdatesShelfWindow,
	val manga: List<Manga>,
)

/** Manga the tracker found updates for within the window chosen in settings, most recent first. */
class ObserveUpdatesShelfUseCase @Inject constructor(
	private val repository: TrackingRepository,
	private val mangaDataRepository: MangaDataRepository,
	private val settings: AppSettings,
) {

	operator fun invoke(filterOptions: Set<ListFilterOption>): Flow<UpdatesShelf> =
		observeWindowStart().flatMapLatest { (window, start) ->
			repository.observeTrackingLog(0, filterOptions).map { log ->
				val overrides = mangaDataRepository.getOverrides()
				val manga = if (start == null) {
					emptyList()
				} else {
					log.filter { it.createdAt.toEpochMilli() >= start }
						.distinctBy { it.manga.id }
						.map { it.manga.withOverride(overrides[it.manga.id]) }
				}
				UpdatesShelf(window, manga)
			}
		}

	private fun observeWindowStart(): Flow<Pair<UpdatesShelfWindow, Long?>> = combine(
		settings.observeAsFlow(AppSettings.KEY_UPDATES_SHELF_WINDOW) { updatesShelfWindow },
		settings.observeAsFlow(AppSettings.KEY_TRACKER_LAST_RUN) { trackerLastRunAt },
		::Pair,
	).flatMapLatest { (window, lastRun) ->
		flow {
			do {
				emit(window to updatesShelfStart(window, lastRun, System.currentTimeMillis()))
				if (window == UpdatesShelfWindow.LAST_24_HOURS) {
					delay(ROLLING_WINDOW_REFRESH_MS)
				}
			} while (window == UpdatesShelfWindow.LAST_24_HOURS)
		}
	}
}
