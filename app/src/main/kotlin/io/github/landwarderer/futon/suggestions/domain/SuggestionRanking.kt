package io.github.landwarderer.futon.suggestions.domain

import java.time.Duration
import java.time.Instant

/**
 * The local ranking decisions, kept free of Android code so each one can be tested on its own.
 */
object SuggestionRanking {

	/** Under a tenth read... */
	const val DROPPED_MAX_PERCENT = 0.1f

	/** ...and untouched this long counts as left, not merely paused. */
	val DROPPED_AFTER: Duration = Duration.ofDays(21)

	/**
	 * Opened, barely read and left for weeks. [percent] is the reading progress from history, which
	 * is negative when unknown - and unknown is not evidence of anything.
	 */
	fun isDropped(percent: Float, updatedAt: Instant, now: Instant): Boolean =
		percent in 0f..DROPPED_MAX_PERCENT && Duration.between(updatedAt, now) > DROPPED_AFTER

	/** Tag titles compared across sources: `Action`, ` action ` and `ACTION` are one tag. */
	fun normalizeTag(title: String): String = title.trim().lowercase()

	/**
	 * The reader's most telling tags, most telling first: frequent in what they read and rare in
	 * everything else - TF-IDF, with [rarity] as the inverse document frequency.
	 *
	 * Ranking by frequency alone always crowns `action` or `fantasy`, and searching a source for those
	 * returns its most popular titles rather than anything close to this reader. A tag on only one of
	 * the reader's manga comes after every recurring one however rare it is: one title's odd tag is
	 * not a taste.
	 *
	 * @param seedTags the tag titles of every manga read, one entry per manga carrying it
	 * @return original titles, as the sources spell them
	 */
	fun distinctiveTags(seedTags: List<String>, rarity: (String) -> Double, limit: Int): List<String> {
		val counts = LinkedHashMap<String, Pair<String, Int>>()
		seedTags.forEach { title ->
			val key = normalizeTag(title)
			if (key.isEmpty()) return@forEach
			val existing = counts[key]
			counts[key] = (existing?.first ?: title) to ((existing?.second ?: 0) + 1)
		}
		val (recurring, once) = counts.values.partition { it.second >= 2 }
		fun rank(tags: List<Pair<String, Int>>) = tags
			.sortedByDescending { (title, count) -> count * rarity(title) }
			.map { it.first }
		return (rank(recurring) + rank(once)).take(limit)
	}

	/**
	 * Overlap of two tag sets with each tag weighted by its [rarity]: sharing `villainess` counts for
	 * far more than sharing `action`, and a manga carrying thirty tags does not win by sheer number.
	 */
	fun weightedOverlap(a: Set<String>, b: Set<String>, rarity: (String) -> Double): Double {
		if (a.isEmpty() || b.isEmpty()) {
			return 0.0
		}
		val shared = a.sumOf { if (it in b) rarity(it) else 0.0 }
		val union = a.sumOf(rarity) + b.sumOf { if (it in a) 0.0 else rarity(it) }
		return if (union > 0.0) shared / union else 0.0
	}
}
