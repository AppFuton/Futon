package io.github.landwarderer.futon.history.domain

import io.github.landwarderer.futon.history.domain.model.ReadingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HistoryViewsMathTest {

	private val now = 10_000L * 86_400_000L
	private val day = 86_400_000L
	private val minute = 60_000L

	@Test
	fun recentSessionOutweighsEqualOlderSession() {
		val recent = activityScore(now, listOf(ReadingSession(now - day, 30 * minute, 10)), now)
		val older = activityScore(now, listOf(ReadingSession(now - 20 * day, 30 * minute, 10)), now)
		assertTrue(recent > older)
	}

	@Test
	fun sessionHalvesAfterOneHalfLife() {
		val score = activityScore(now, listOf(ReadingSession(now - 5 * day, 60 * minute, 1)), now, halfLifeDays = 5.0)
		assertEquals(30.0, score, 0.001)
	}

	@Test
	fun withoutSessionsNewerReadRanksHigher() {
		assertTrue(activityScore(now - day, emptyList(), now) > activityScore(now - 9 * day, emptyList(), now))
	}

	@Test
	fun spineWindowFindsDetailOffCenter() {
		val energy = FloatArray(SPINE_COLUMNS) { if (it in 40..46) 100f else 1f }
		val start = bestSpineWindow(energy, 8)
		assertTrue("start was $start", start in 39..40)
	}

	@Test
	fun spineWindowPrefersCenterOnFlatCover() {
		val start = bestSpineWindow(FloatArray(SPINE_COLUMNS) { 1f }, 10)
		assertEquals(21f, start.toFloat(), 1f)
	}

	@Test
	fun spineEnergyIgnoresTitleBand() {
		val columns = 10
		val rows = 20
		val luma = FloatArray(columns * rows)
		for (column in 0 until columns step 2) {
			luma[19 * columns + column] = 255f
			luma[18 * columns + column] = 255f
		}
		assertTrue(spineColumnEnergy(luma, columns, rows).all { it == 0f })
	}

	@Test
	fun spineWindowIsClampedToCover() {
		assertEquals(0, bestSpineWindow(FloatArray(4) { 1f }, 99))
	}

	@Test
	fun calendarBlanksFollowFirstDayOfWeek() {
		val october = LocalDate.of(2026, 10, 1)
		assertEquals(3, leadingCalendarBlanks(october, DayOfWeek.MONDAY))
		assertEquals(4, leadingCalendarBlanks(october, DayOfWeek.SUNDAY))
	}
}
