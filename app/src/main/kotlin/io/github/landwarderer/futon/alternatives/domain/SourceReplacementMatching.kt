package io.github.landwarderer.futon.alternatives.domain

import io.github.landwarderer.futon.core.model.chaptersCount
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.levenshteinDistance
import java.util.Locale
import kotlin.math.roundToInt

/** The pure scoring rules behind [SourceReplacementPlannerUseCase]. */
internal object SourceReplacementMatching {

	/** Best similarity, 0..1, between any title (main or alternative) of [candidate] and of [reference]. */
	fun titleSimilarity(candidate: Manga, reference: Manga): Float {
		val candidateTitles = (listOf(candidate.title) + candidate.altTitles).filter(String::isNotBlank)
		val referenceTitles = (listOf(reference.title) + reference.altTitles).filter(String::isNotBlank)
		return candidateTitles.maxOfOrNull { title ->
			referenceTitles.maxOfOrNull { ref -> normalizedSimilarity(title, ref) } ?: 0f
		} ?: 0f
	}

	fun normalizedSimilarity(first: String, second: String): Float {
		val a = first.trim().lowercase(Locale.ROOT)
		val b = second.trim().lowercase(Locale.ROOT)
		val maxLength = maxOf(a.length, b.length)
		return if (maxLength == 0) 1f else 1f - a.levenshteinDistance(b).toFloat() / maxLength
	}

	/** True when a candidate with [similarity]/[chapters] beats the current best one. */
	fun isBetter(similarity: Float, chapters: Int, bestSimilarity: Float, bestChapters: Int): Boolean {
		return similarity > bestSimilarity || (similarity == bestSimilarity && chapters > bestChapters)
	}

	fun isBetter(candidate: Manga, candidateSimilarity: Float, best: Manga, bestSimilarity: Float): Boolean =
		isBetter(candidateSimilarity, candidate.chaptersCount(), bestSimilarity, best.chaptersCount())

	/**
	 * Up to [limit] titles spread evenly across the alphabet, so a large library is compared on a sample
	 * that is not biased towards whatever was read last.
	 */
	fun representativeSample(manga: List<Manga>, limit: Int): List<Manga> {
		if (manga.size <= limit) {
			return manga
		}
		if (limit <= 1) {
			return manga.take(limit)
		}
		val sorted = manga.sortedBy { it.title.lowercase(Locale.ROOT) }
		return List(limit) { index ->
			val position = index.toDouble() * sorted.lastIndex / (limit - 1)
			sorted[position.roundToInt()]
		}.distinctBy(Manga::id)
	}

	/** Sources with the most matches first, then the closest titles. */
	val planComparator: Comparator<SourceReplacementPlan> =
		compareByDescending<SourceReplacementPlan> { it.matchedCount }
			.thenByDescending(SourceReplacementPlan::averageTitleSimilarity)
}
