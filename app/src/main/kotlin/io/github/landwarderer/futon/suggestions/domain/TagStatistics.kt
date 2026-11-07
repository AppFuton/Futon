package io.github.landwarderer.futon.suggestions.domain

import io.github.landwarderer.futon.core.db.MangaDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * How common each tag is across everything the app has stored - every list browsed, every
 * suggestion, every manga opened, from every source.
 *
 * That is the background a reader's taste is measured against: reading a lot of `action` says
 * little when nearly everything is tagged `action`, while reading a lot of `villainess` says a great
 * deal. Counted on the device, from what is already in its database.
 */
@Singleton
class TagStatistics @Inject constructor(
	private val db: MangaDatabase,
) {

	private val mutex = Mutex()
	private var cached: Snapshot? = null

	/** Cached for a few minutes: a details screen asks on every open, and this is a full scan. */
	suspend fun snapshot(): Snapshot = mutex.withLock {
		cached?.takeIf { System.currentTimeMillis() - it.takenAt < MAX_AGE_MS }?.let { return it }
		val dao = db.getTagsDao()
		Snapshot(
			documentFrequency = dao.countMangaPerTag().associate { it.title to it.count },
			total = dao.countTaggedManga(),
			takenAt = System.currentTimeMillis(),
		).also { cached = it }
	}

	class Snapshot(
		private val documentFrequency: Map<String, Int>,
		private val total: Int,
		val takenAt: Long,
	) {

		/** How many stored manga carry [title]. */
		fun frequency(title: String): Int = documentFrequency[SuggestionRanking.normalizeTag(title)] ?: 0

		/** Inverse document frequency, smoothed so an unseen tag is rare rather than infinite. */
		fun rarity(title: String): Double = ln((total + 1.0) / (frequency(title) + 1.0)) + 1.0
	}

	private companion object {
		const val MAX_AGE_MS = 10 * 60 * 1000L
	}
}
