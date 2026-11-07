package io.github.landwarderer.futon.alternatives.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val MAX_COMPARISON_SAMPLE = 25
private const val MAX_COMPARISON_PARALLELISM_PER_SOURCE = 2
private const val MAX_COMPARISON_TIMEOUTS_PER_SOURCE = 2
private const val COMPARISON_REQUEST_TIMEOUT_MS = 15_000L
private const val WEBVIEW_COMPARISON_REQUEST_TIMEOUT_MS = 30_000L

/**
 * Works out which sources can take over a set of titles: it searches a representative sample of the
 * titles on every candidate source and ranks the sources by how many of them they have.
 */
class SourceReplacementPlannerUseCase @Inject constructor(
	private val alternativesUseCase: AlternativesUseCase,
) {

	suspend operator fun invoke(
		manga: List<Manga>,
		sourceScope: AlternativeSourceScope,
		excludedSourceNames: Set<String> = emptySet(),
		onProgress: (SourceComparisonProgress) -> Unit = {},
		onSourceFailed: (MangaSource) -> Unit = {},
	): List<SourceReplacementPlan> = coroutineScope {
		val seeds = SourceReplacementMatching.representativeSample(manga.distinctBy(Manga::id), MAX_COMPARISON_SAMPLE)
		if (seeds.isEmpty()) {
			return@coroutineScope emptyList()
		}
		val sources = alternativesUseCase.getCandidateSources(
			ref = seeds.first().source,
			sourceScope = sourceScope,
			sameLanguageOnly = true,
		).filter { it.name !in excludedSourceNames }
		val totalChecks = sources.size * seeds.size
		val progressLock = Any()
		var completedChecks = 0
		var completedSources = 0
		fun reportProgress(titleCompleted: Boolean = false, sourceCompleted: Boolean = false) {
			synchronized(progressLock) {
				if (titleCompleted) completedChecks++
				if (sourceCompleted) completedSources++
				onProgress(
					SourceComparisonProgress(
						completedChecks = completedChecks,
						totalChecks = totalChecks,
						remainingSources = sources.size - completedSources,
						totalSources = sources.size,
						sampleSize = seeds.size,
					),
				)
			}
		}
		reportProgress()
		val plans = sources.map { source ->
			async {
				val comparison = compareSource(source, seeds) {
					reportProgress(titleCompleted = true)
				}
				if (comparison.failed) {
					// The very first request already failed: the source is down, blocked or needs a captcha.
					synchronized(progressLock) {
						onSourceFailed(source)
					}
					null
				} else {
					SourceReplacementPlan(
						source = source,
						matches = comparison.matches.mapValues { it.value.manga },
						total = seeds.size,
						averageTitleSimilarity = comparison.matches.values
							.map(ScoredMatch::titleSimilarity)
							.takeIf(List<Float>::isNotEmpty)
							?.average()
							?.toFloat()
							?: 0f,
					)
				}.also { reportProgress(sourceCompleted = true) }
			}
		}.awaitAll().filterNotNull()
		plans.filter { it.matchedCount > 0 }.sortedWith(SourceReplacementMatching.planComparator)
	}

	/** Finds the best match for each of [manga] on [source], skipping the ones in [knownMatches]. */
	suspend fun resolve(
		manga: List<Manga>,
		source: MangaSource,
		knownMatches: Map<Long, Manga> = emptyMap(),
		onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
	): Map<Long, Manga> = coroutineScope {
		val seeds = manga.distinctBy(Manga::id)
		val seedIds = seeds.mapTo(HashSet(), Manga::id)
		val result = knownMatches.filterKeys(seedIds::contains).toMutableMap()
		val completed = AtomicInteger(result.size)
		onProgress(completed.get(), seeds.size)
		val resolved = seeds.filterNot { it.id in result }.map { seed ->
			async {
				findMatch(seed, source).also {
					onProgress(completed.incrementAndGet(), seeds.size)
				}
			}
		}.awaitAll()
		resolved.filterNotNull().forEach { match -> result[match.seedId] = match.manga }
		result
	}

	private suspend fun compareSource(
		source: MangaSource,
		seeds: List<Manga>,
		onTitleChecked: () -> Unit,
	): SourceComparisonResult {
		val isWebView = alternativesUseCase.isWebViewSource(source)
		val parallelism = if (isWebView) 1 else MAX_COMPARISON_PARALLELISM_PER_SOURCE
		val timeout = if (isWebView) WEBVIEW_COMPARISON_REQUEST_TIMEOUT_MS else COMPARISON_REQUEST_TIMEOUT_MS
		val matches = HashMap<Long, ScoredMatch>()
		val initialOutcome = compareTitle(source, seeds.first(), timeout)
		onTitleChecked()
		initialOutcome.match?.let { match -> matches[match.seedId] = match }
		if (initialOutcome.failed) {
			repeat(seeds.size - 1) { onTitleChecked() }
			return SourceComparisonResult(matches = matches, failed = true)
		}
		var processed = 1
		var timeouts = 0
		for (batch in seeds.drop(1).chunked(parallelism)) {
			if (timeouts >= MAX_COMPARISON_TIMEOUTS_PER_SOURCE) {
				break
			}
			val outcomes = coroutineScope {
				batch.map { seed ->
					async { compareTitle(source, seed, timeout) }
				}.awaitAll()
			}
			processed += batch.size
			outcomes.forEach { outcome ->
				onTitleChecked()
				if (outcome.timedOut) {
					timeouts++
				}
				outcome.match?.let { match -> matches[match.seedId] = match }
			}
		}
		repeat(seeds.size - processed) { onTitleChecked() }
		return SourceComparisonResult(matches = matches, failed = false)
	}

	private suspend fun compareTitle(
		source: MangaSource,
		seed: Manga,
		timeout: Long,
	): ComparisonOutcome {
		val requestFailed = AtomicBoolean(false)
		return withTimeoutOrNull(timeout) {
			ComparisonOutcome(
				match = findMatch(
					seed = seed,
					source = source,
					loadDetails = false,
					onFailure = { requestFailed.set(true) },
				),
				timedOut = false,
				failed = requestFailed.get(),
			)
		} ?: ComparisonOutcome(match = null, timedOut = true, failed = true)
	}

	private suspend fun findMatch(
		seed: Manga,
		source: MangaSource,
		loadDetails: Boolean = true,
		onFailure: (() -> Unit)? = null,
	): ScoredMatch? {
		var best: ScoredMatch? = null
		alternativesUseCase.searchSource(
			manga = seed,
			source = source,
			loadDetails = loadDetails,
			onFailure = onFailure,
		).collect { candidate ->
			val scored = ScoredMatch(
				seedId = seed.id,
				manga = candidate,
				titleSimilarity = SourceReplacementMatching.titleSimilarity(candidate, seed),
			)
			val current = best
			if (current == null || SourceReplacementMatching.isBetter(
					candidate = scored.manga,
					candidateSimilarity = scored.titleSimilarity,
					best = current.manga,
					bestSimilarity = current.titleSimilarity,
				)
			) {
				best = scored
			}
		}
		return best
	}

	private data class ScoredMatch(
		val seedId: Long,
		val manga: Manga,
		val titleSimilarity: Float,
	)

	private data class ComparisonOutcome(
		val match: ScoredMatch?,
		val timedOut: Boolean,
		val failed: Boolean,
	)

	private data class SourceComparisonResult(
		val matches: Map<Long, ScoredMatch>,
		val failed: Boolean,
	)
}

data class SourceReplacementPlan(
	val source: MangaSource,
	val matches: Map<Long, Manga>,
	val total: Int,
	val averageTitleSimilarity: Float,
) {
	val matchedCount: Int
		get() = matches.size

	val matchRate: Float
		get() = if (total == 0) 0f else matchedCount.toFloat() / total
}

data class SourceComparisonProgress(
	val completedChecks: Int,
	val totalChecks: Int,
	val remainingSources: Int,
	val totalSources: Int,
	val sampleSize: Int,
)
