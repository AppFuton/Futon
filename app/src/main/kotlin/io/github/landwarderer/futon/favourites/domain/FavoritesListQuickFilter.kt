package io.github.landwarderer.futon.favourites.domain

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.list.domain.MangaListQuickFilter
import org.koitharu.kotatsu.parsers.model.ContentType

class FavoritesListQuickFilter @AssistedInject constructor(
	@Assisted private val categoryId: Long,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		add(ListFilterOption.ContentType(ContentType.MANGA))
		add(ListFilterOption.ContentType(ContentType.MANHWA))
		add(ListFilterOption.ContentType(ContentType.MANHUA))
		// Blacklisted tags are ones the user asked not to see; do not advertise them as quick filters.
		val blacklist = settings.tagsBlacklist.mapTo(HashSet()) { it.lowercase() }
		repository.findPopularTagTitles(categoryId, POPULAR_TAGS_COUNT + blacklist.size)
			.asSequence()
			.filterNot { it.lowercase() in blacklist }
			.take(POPULAR_TAGS_COUNT)
			.mapTo(this) { ListFilterOption.TagTitle(it) }
		repository.findPopularSources(categoryId, 3).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	private companion object {

		const val POPULAR_TAGS_COUNT = 3
	}

	@AssistedFactory
	interface Factory {

		fun create(categoryId: Long): FavoritesListQuickFilter
	}
}
