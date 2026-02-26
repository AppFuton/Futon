package io.github.landwarderer.futon.history.domain

import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.list.domain.MangaListQuickFilter
import javax.inject.Inject

class HistoryListQuickFilter @Inject constructor(
	private val settings: AppSettings,
	private val repository: HistoryRepository,
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
        add(ListFilterOption.Macro.READING)
		add(ListFilterOption.Macro.FAVORITE)
		add(ListFilterOption.NOT_FAVORITE)
		if (!settings.isNsfwContentDisabled) {
			add(ListFilterOption.Macro.NSFW)
		}
		repository.getPopularTags(3).mapTo(this) {
			ListFilterOption.Tag(it)
		}
		repository.getPopularSources(3).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}
}
