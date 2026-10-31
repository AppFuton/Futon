package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.mihon.MihonMangaRepository
import io.github.landwarderer.futon.suggestions.domain.SuggestionRanking
import io.github.landwarderer.futon.suggestions.domain.SuggestionRepository
import io.github.landwarderer.futon.suggestions.domain.TagStatistics
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * What to read next, given this manga - for every manga, not only the ones the source links.
 *
 * The source's own related list comes first. Plenty of parsers link nothing, so when it comes up
 * short the row is filled from this manga's tags: first the reader's own stored suggestions that
 * share them, which costs nothing, then one search of this manga's source for its least common tags.
 */
class RelatedMangaUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val suggestionRepository: SuggestionRepository,
	private val dataRepository: MangaDataRepository,
	private val tagStatistics: TagStatistics,
	private val settings: AppSettings,
) {

	suspend operator fun invoke(seed: Manga): List<Manga>? = coroutineScope {
		val excludeNsfw = settings.isNsfwContentDisabled
		val blacklist = settings.tagsBlacklist.mapTo(HashSet(), SuggestionRanking::normalizeTag)
		val sourceJob = async { sourceRelated(seed) }
		val stored = runCatchingCancellable { suggestionRepository.getTopList(MAX_STORED) }
			.onFailure { it.printStackTraceDebug("RelatedMangaUseCase::stored") }
			.getOrDefault(emptyList())

		val picked = LinkedHashMap<Long, Manga>()
		fun add(list: List<Manga>) = list.forEach {
			if (it.id != seed.id && !(excludeNsfw && it.isNsfw()) && !it.isBlacklisted(blacklist)) {
				picked.putIfAbsent(it.id, it)
			}
		}
		add(sourceJob.await().orEmpty())
		if (picked.size >= MIN_RESULTS) {
			return@coroutineScope picked.values.take(LIMIT)
		}
		// The details may not have loaded - a source behind a challenge, offline - and then the manga
		// carries no tags; the ones stored when it last loaded are just as good for this.
		val tagged = if (seed.tags.isEmpty()) {
			runCatchingCancellable { dataRepository.findMangaById(seed.id, withChapters = false) }
				.getOrNull()?.takeIf { it.tags.isNotEmpty() } ?: seed
		} else {
			seed
		}
		// Blacklisted tags say nothing the reader wants more of, so they are never matched on.
		val seedTags = tagged.tags.filterNot { SuggestionRanking.normalizeTag(it.title) in blacklist }
		if (seedTags.isEmpty()) {
			return@coroutineScope picked.values.take(LIMIT).ifEmpty { null }
		}
		val rarity = runCatchingCancellable { tagStatistics.snapshot() }.getOrNull()
			?.let { snapshot -> { title: String -> snapshot.rarity(title) } }
			?: { _: String -> 1.0 }
		add(likeByTags(seedTags, stored, rarity))
		if (picked.size < MIN_RESULTS) {
			add(sourceByTags(tagged, seedTags, rarity))
		}
		picked.values.take(LIMIT).ifEmpty { null }
	}

	private suspend fun sourceRelated(seed: Manga): List<Manga>? = runCatchingCancellable {
		mangaRepositoryFactory.create(seed.source).getRelated(seed)
	}.onFailure { it.printStackTraceDebug("RelatedMangaUseCase::sourceRelated") }.getOrNull()

	/**
	 * The reader's stored suggestions that share this manga's tags, closest first - already
	 * resolved and on the device, so no request at all. Closeness weighs each shared tag by how
	 * rare it is, so sharing `villainess` beats sharing `action`.
	 */
	private fun likeByTags(seedTags: Collection<MangaTag>, stored: List<Manga>, rarity: (String) -> Double): List<Manga> {
		val seedTitles = seedTags.titles()
		// One shared tag is a coincidence unless the manga has hardly any
		val minShared = if (seedTitles.size <= 2) 1 else 2
		return stored.mapNotNull { manga ->
			val tags = manga.tags.titles()
			if (tags.count { it in seedTitles } < minShared) {
				null
			} else {
				manga to SuggestionRanking.weightedOverlap(seedTitles, tags, rarity)
			}
		}.sortedByDescending { it.second }
			.map { it.first }
			.take(LIMIT)
	}

	/**
	 * One search of this manga's own source for its rarest tags.
	 *
	 * Tags are matched against the source's own filter tags by title, so this also works for sources
	 * whose details tags are not usable as filters (extension sources), and never searches with no
	 * tag at all - that would be the source's whole catalogue. Never sorted by popularity first.
	 */
	private suspend fun sourceByTags(
		seed: Manga,
		seedTags: Collection<MangaTag>,
		rarity: (String) -> Double,
	): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(seed.source)
		if (repository is MihonMangaRepository) {
			// Its list paging is stateful and the instance is shared, so a background search here
			// would make the source list the reader came from skip or repeat a page.
			return@runCatchingCancellable emptyList()
		}
		val available = repository.getFilterOptions().availableTags
			.associateBy { SuggestionRanking.normalizeTag(it.title) }
		val byRarity = seedTags.sortedByDescending { rarity(it.title) }
			.mapNotNull { available[SuggestionRanking.normalizeTag(it.title)] }
			.distinct()
		val first = byRarity.firstOrNull() ?: return@runCatchingCancellable emptyList()
		val order = PREFERRED_ORDERS.firstOrNull { it in repository.sortOrders }
		val seedTitles = seedTags.titles()
		val tagCount = if (repository.filterCapabilities.isMultipleTagsSupported) 2 else 1
		var found = repository.getList(0, order, MangaListFilter(tags = byRarity.take(tagCount).toSet()))
		if (found.none { it.id != seed.id } && byRarity.size > 1 && tagCount > 1) {
			// Two rare tags together can match nothing but the manga itself
			found = repository.getList(0, order, MangaListFilter(tags = setOf(first)))
		}
		found.filter { it.id != seed.id }
			.sortedByDescending { SuggestionRanking.weightedOverlap(seedTitles, it.tags.titles(), rarity) }
			.take(LIMIT)
	}.onFailure { it.printStackTraceDebug("RelatedMangaUseCase::sourceByTags") }.getOrDefault(emptyList())

	private fun Collection<MangaTag>.titles(): Set<String> = mapTo(HashSet()) { SuggestionRanking.normalizeTag(it.title) }

	private fun Manga.isBlacklisted(blacklist: Set<String>): Boolean =
		blacklist.isNotEmpty() && tags.any { SuggestionRanking.normalizeTag(it.title) in blacklist }

	private companion object {
		const val LIMIT = 20

		/** Fewer than this and the row looks empty, so the next source of answers is asked too. */
		const val MIN_RESULTS = 4

		/** The reader's suggestions to match tags against; they are stored best first. */
		const val MAX_STORED = 200

		/** Anything but popularity, which would hand back the same best-sellers for every manga. */
		val PREFERRED_ORDERS = listOf(SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.RATING, SortOrder.POPULARITY)
	}
}
