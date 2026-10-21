package io.github.landwarderer.futon.history.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reader progress saves run as independent coroutines, so they can reach the database in any order.
 * A sequence number taken when a save is requested lets an older save be dropped once a newer one for
 * the same manga has been written, instead of overwriting it.
 */
@Singleton
class HistorySaveOrder @Inject constructor() {

	private val counter = AtomicLong()
	private val mutex = Mutex()
	private val written = HashMap<Long, Long>()

	/** Must be called when the save is requested, not when it runs. */
	fun next(): Long = counter.incrementAndGet()

	/** Runs [block] unless a newer save for [mangaId] was already written. Returns false if skipped. */
	suspend fun runIfLatest(mangaId: Long, sequence: Long, block: suspend () -> Unit): Boolean = mutex.withLock {
		if (sequence <= (written[mangaId] ?: 0L)) {
			return@withLock false
		}
		block()
		written[mangaId] = sequence
		true
	}
}
