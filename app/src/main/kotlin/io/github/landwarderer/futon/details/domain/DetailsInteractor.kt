package io.github.landwarderer.futon.details.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import io.github.landwarderer.futon.core.model.FavouriteCategory
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.TriStateOption
import io.github.landwarderer.futon.core.prefs.observeAsFlow
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.favourites.domain.FavouritesRepository
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.parsers.model.Manga
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.scrobbling.common.domain.Scrobbler
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblingInfo
import io.github.landwarderer.futon.tracker.domain.TrackingRepository
import javax.inject.Inject
import javax.inject.Provider

/* TODO: remove */
class DetailsInteractor @Inject constructor(
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val localMangaRepository: LocalMangaRepository,
	private val trackingRepository: TrackingRepository,
	private val settings: AppSettings,
	private val scrobblersProvider: Provider<Set<@JvmSuppressWildcards Scrobbler>>,
) {
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler> by lazy { scrobblersProvider.get() }

	fun observeFavourite(mangaId: Long): Flow<Set<FavouriteCategory>> {
		return favouritesRepository.observeCategories(mangaId)
	}

	fun observeNewChapters(mangaId: Long): Flow<Int> {
		return settings.observeAsFlow(AppSettings.KEY_TRACKER_ENABLED) { isTrackerEnabled }
			.flatMapLatest { isEnabled ->
				if (isEnabled) {
					trackingRepository.observeNewChaptersCount(mangaId)
				} else {
					flowOf(0)
				}
			}
	}

	fun observeScrobblingInfo(mangaId: Long): Flow<List<ScrobblingInfo>> {
		return combine(
			scrobblers.map { it.observeScrobblingInfo(mangaId) },
		) { scrobblingInfo ->
			scrobblingInfo.filterNotNull()
		}
	}

	fun observeIncognitoMode(mangaFlow: Flow<Manga?>): Flow<TriStateOption> {
		return mangaFlow
			.filterNotNull()
			.distinctUntilChangedBy { it.isNsfw() }
			.combine(observeIncognitoMode()) { manga, globalIncognito ->
				when {
					globalIncognito -> TriStateOption.ENABLED
					manga.isNsfw() -> settings.incognitoModeForNsfw
					else -> TriStateOption.DISABLED
				}
			}
	}

	suspend fun updateLocal(subject: MangaDetails?, localManga: LocalManga): MangaDetails? {
		subject ?: return null
		return if (subject.id == localManga.manga.id) {
			if (subject.isLocal) {
				subject.copy(
					manga = localManga.manga,
				)
			} else {
				subject.copy(
					localManga = runCatchingCancellable {
						localManga.copy(
							manga = localMangaRepository.getDetails(localManga.manga),
						)
					}.getOrNull() ?: subject.local,
				)
			}
		} else {
			subject
		}
	}

	suspend fun findRemote(seed: Manga) = localMangaRepository.getRemoteManga(seed)

	private fun observeIncognitoMode() = settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE) {
		isIncognitoModeEnabled
	}
}
