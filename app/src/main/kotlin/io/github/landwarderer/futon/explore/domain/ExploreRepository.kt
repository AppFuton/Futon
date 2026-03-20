package io.github.landwarderer.futon.explore.domain

import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.asArrayList
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.almostEquals
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.suggestions.domain.TagsBlacklist
import javax.inject.Inject

class ExploreRepository @Inject constructor(
	private val settings: AppSettings,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend fun findRandomManga(tagsLimit: Int): Manga {
		val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val tags = historyRepository.getPopularTags(tagsLimit).mapNotNull {
			if (it in tagsBlacklist) null else it.title
		}
		val sources = sourcesRepository.getEnabledSources()
		check(sources.isNotEmpty()) { "No sources available" }
		for (i in 0..4) {
			val list = getList(sources.random(), tags, tagsBlacklist)
			val manga = list.randomOrNull() ?: continue
			val details = runCatchingCancellable {
				mangaRepositoryFactory.create(manga.source).getDetails(manga)
			}.getOrNull() ?: continue
			if ((settings.isSuggestionsExcludeNsfw && details.isNsfw()) || details in tagsBlacklist) {
				continue
			}
			return details
		}
		throw NoSuchElementException()
	}

	suspend fun findRandomManga(source: MangaSource, tagsLimit: Int): Manga {
		val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val skipNsfw = settings.isSuggestionsExcludeNsfw && !source.isNsfw()
		val tags = historyRepository.getPopularTags(tagsLimit).mapNotNull {
			if (it in tagsBlacklist) null else it.title
		}
		for (i in 0..4) {
			val list = getList(source, tags, tagsBlacklist)
			val manga = list.randomOrNull() ?: continue
			val details = runCatchingCancellable {
				mangaRepositoryFactory.create(manga.source).getDetails(manga)
			}.getOrNull() ?: continue
			if ((skipNsfw && details.isNsfw()) || details in tagsBlacklist) {
				continue
			}
			return details
		}
		throw NoSuchElementException()
	}

	private suspend fun getList(
		source: MangaSource,
		tags: List<String>,
		blacklist: TagsBlacklist,
	): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(source)
		val order = repository.sortOrders.random()
		val availableTags = repository.getFilterOptions().availableTags
		val tag = tags.firstNotNullOfOrNull { title ->
			availableTags.find { x -> x.title.almostEquals(title, 0.4f) }
		}
		val list = repository.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(tags = setOfNotNull(tag)),
		).asArrayList()
		if (settings.isSuggestionsExcludeNsfw) {
			list.removeAll { it.isNsfw() }
		}
		if (blacklist.isNotEmpty()) {
			list.removeAll { manga -> manga in blacklist }
		}
		list.shuffle()
		list
	}.onFailure {
		it.printStackTraceDebug("ExploreRepository::getList")
	}.getOrDefault(emptyList())
}
