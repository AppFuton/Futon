package io.github.landwarderer.futon.tracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import io.github.landwarderer.futon.core.model.withOverride
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

/** Every manga with new chapters, showing the user's custom titles and covers. */
class ObserveUpdatesMapUseCase @Inject constructor(
	private val repository: TrackingRepository,
	private val mangaDataRepository: MangaDataRepository,
) {

	operator fun invoke(filterOptions: Set<ListFilterOption>): Flow<List<MangaTracking>> =
		repository.observeUpdatedManga(limit = 0, filterOptions = filterOptions)
			.distinctUntilChangedBy(::mapContent)
			.map { list ->
				val overrides = mangaDataRepository.getOverrides()
				list.map { it.copy(manga = it.manga.withOverride(overrides[it.manga.id])) }
			}
}

/**
 * What the map draws for each manga. The tracker rewrites the check time of every manga it looks at, which would
 * otherwise redraw the map once per manga checked instead of once per update found.
 */
internal fun mapContent(list: List<MangaTracking>): Map<Long, Pair<Manga, Int>> =
	list.associate { it.manga.id to (it.manga to it.newChapters) }
