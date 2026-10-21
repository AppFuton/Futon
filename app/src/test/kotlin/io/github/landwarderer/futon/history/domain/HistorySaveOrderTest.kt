package io.github.landwarderer.futon.history.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySaveOrderTest {

	private val order = HistorySaveOrder()

	@Test
	fun `older save that runs last does not overwrite newer one`() = runTest {
		val stale = order.next() // e.g. a wrong position from a stale page callback
		val fresh = order.next() // the correction requested right after it
		val saved = mutableListOf<String>()
		assertTrue(order.runIfLatest(MANGA, fresh) { saved += "fresh" })
		assertFalse(order.runIfLatest(MANGA, stale) { saved += "stale" })
		assertEquals(listOf("fresh"), saved)
	}

	@Test
	fun `saves in request order are all written`() = runTest {
		val saved = mutableListOf<Int>()
		val sequences = List(3) { order.next() }
		sequences.forEachIndexed { i, seq ->
			assertTrue(order.runIfLatest(MANGA, seq) { saved += i })
		}
		assertEquals(listOf(0, 1, 2), saved)
	}

	@Test
	fun `ordering is tracked per manga`() = runTest {
		val first = order.next()
		val second = order.next()
		assertTrue(order.runIfLatest(OTHER_MANGA, second) {})
		assertTrue(order.runIfLatest(MANGA, first) {})
	}

	@Test
	fun `failed save does not block an older pending one`() = runTest {
		val older = order.next()
		val newer = order.next()
		runCatching { order.runIfLatest(MANGA, newer) { error("write failed") } }
		assertTrue(order.runIfLatest(MANGA, older) {})
	}

	@Test
	fun `concurrent saves cannot interleave`() = runTest {
		val stale = order.next()
		val fresh = order.next()
		val staleStarted = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val saved = mutableListOf<String>()
		val staleJob = async {
			order.runIfLatest(MANGA, stale) {
				staleStarted.complete(Unit)
				release.await()
				saved += "stale"
			}
		}
		staleStarted.await()
		val freshJob = async { order.runIfLatest(MANGA, fresh) { saved += "fresh" } }
		yield()
		release.complete(Unit)
		assertTrue(staleJob.await())
		assertTrue(freshJob.await())
		assertEquals(listOf("stale", "fresh"), saved) // the newer save always lands last
	}

	private companion object {
		const val MANGA = 1L
		const val OTHER_MANGA = 2L
	}
}
