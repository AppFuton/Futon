package io.github.landwarderer.futon.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesMapMathTest {

	@Test
	fun squarifyFillsAreaWithoutLosingItems() {
		val rects = squarify(listOf(6.0, 6.0, 4.0, 3.0, 2.0, 2.0, 1.0), 0f, 0f, 600f, 400f) { it }
		assertEquals(7, rects.size)
		assertEquals(240_000.0, rects.sumOf { (_, r) -> (r.width * r.height).toDouble() }, 1.0)
		assertTrue(rects.all { (_, r) -> r.x >= -0.01f && r.y >= -0.01f && r.x + r.width <= 600.01f && r.y + r.height <= 400.01f })
	}

	@Test
	fun squarifyAreasFollowWeights() {
		val rects = squarify(listOf(3.0, 1.0), 0f, 0f, 100f, 100f) { it }.toMap()
		assertEquals(7500f, rects.getValue(3.0).let { it.width * it.height }, 1f)
	}

	@Test
	fun squarifySkipsEmptyInput() {
		assertTrue(squarify(listOf(0.0), 0f, 0f, 10f, 10f) { it }.isEmpty())
	}

	@Test
	fun foldKeepsEverythingThatFits() {
		assertEquals(TileFold(3, false), foldTiles(listOf(4.0, 3.0, 3.0), 1000f, 100f, isZoomed = false))
	}

	@Test
	fun foldLeavesASingleSmallTileAlone() {
		assertEquals(TileFold(3, false), foldTiles(listOf(50.0, 49.0, 1.0), 1000f, 100f, isZoomed = false))
	}

	@Test
	fun foldTakesOutTheTail() {
		assertEquals(TileFold(1, false), foldTiles(listOf(97.0, 1.0, 1.0, 1.0), 1000f, 100f, isZoomed = false))
	}

	@Test
	fun rootMayFoldAWholeGroup() {
		assertEquals(TileFold(0, false), foldTiles(List(50) { 1.0 }, 1000f, 100f, isZoomed = false))
	}

	@Test
	fun zoomedLevelAlwaysMakesProgress() {
		for (count in 2..300) {
			val fold = foldTiles(List(count) { 1.0 }, 1000f, 100f, isZoomed = true)
			assertTrue(fold.kept in 1..count)
		}
	}

	@Test
	fun zoomedLevelFitsAsManyEqualTilesAsItCan() {
		assertEquals(TileFold(9, true), foldTiles(List(50) { 1.0 }, 1000f, 100f, isZoomed = true))
	}

	@Test
	fun zoomedLevelKeepsProportionsWhenATileFits() {
		assertFalse(foldTiles(listOf(90.0, 1.0, 1.0, 1.0), 1000f, 100f, isZoomed = true).isEqualSized)
	}


	@Test
	fun shelfOfLastCheckStartsWithThatRun() {
		assertEquals(5_000L, updatesShelfStart(UpdatesShelfWindow.LAST_CHECK, 5_000L, 90_000_000L))
	}

	@Test
	fun shelfIsEmptyBeforeTheFirstCheck() {
		assertNull(updatesShelfStart(UpdatesShelfWindow.LAST_CHECK, 0L, 90_000_000L))
	}

	@Test
	fun rollingShelfIgnoresTheLastRun() {
		assertEquals(3_600_000L, updatesShelfStart(UpdatesShelfWindow.LAST_24_HOURS, 5_000L, 90_000_000L))
	}
	@Test
	fun mosaicKeepsCoverShapedCells() {
		assertEquals(MosaicGrid(3, 2), mosaicGrid(8, 100f, 100f))
		assertEquals(MosaicGrid(1, 1), mosaicGrid(1, 100f, 100f))
	}

	@Test
	fun mosaicNeverNeedsMoreCoversThanItHas() {
		for (count in 1..20) {
			val grid = mosaicGrid(count, 90f, 140f)
			assertTrue(grid.columns * grid.rows <= minOf(count, 9))
		}
	}
}
