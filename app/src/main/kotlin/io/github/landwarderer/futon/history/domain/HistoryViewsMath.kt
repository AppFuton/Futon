package io.github.landwarderer.futon.history.domain

import io.github.landwarderer.futon.history.domain.model.ReadingSession
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

const val ACTIVITY_HALF_LIFE_DAYS = 5.0
const val SPINE_COLUMNS = 52
const val SPINE_ROWS = 72

private const val MILLIS_PER_DAY = 86_400_000.0
private const val MILLIS_PER_MINUTE = 60_000.0
private const val SPINE_CENTER_BIAS = 0.3f
private const val SPINE_TOP_SKIP = 0.08f
private const val SPINE_BOTTOM_LIMIT = 0.8f

/**
 * Reading minutes where each session counts half as much every [halfLifeDays].
 * Entries without sessions, which is every entry while statistics are off, decay from their last read date instead.
 */
fun activityScore(
	updatedAt: Long,
	sessions: List<ReadingSession>,
	now: Long,
	halfLifeDays: Double = ACTIVITY_HALF_LIFE_DAYS,
): Double {
	fun decay(time: Long) = 0.5.pow(max(0L, now - time) / MILLIS_PER_DAY / halfLifeDays)
	if (sessions.isEmpty()) {
		return decay(updatedAt)
	}
	return sessions.sumOf { it.duration / MILLIS_PER_MINUTE * decay(it.startedAt) }
}

/**
 * Local contrast summed per column of a [columns] by [rows] luminance grid in row order.
 * The top and bottom of the cover are skipped so logos and title bars do not decide the crop.
 */
fun spineColumnEnergy(luma: FloatArray, columns: Int, rows: Int): FloatArray {
	require(luma.size == columns * rows) { "Expected ${columns * rows} samples, got ${luma.size}" }
	val energy = FloatArray(columns)
	val firstRow = (rows * SPINE_TOP_SKIP).toInt()
	val lastRow = min(rows - 1, (rows * SPINE_BOTTOM_LIMIT).toInt())
	for (row in firstRow until lastRow) {
		for (column in 0 until columns - 1) {
			val current = luma[row * columns + column]
			energy[column] += abs(current - luma[row * columns + column + 1]) +
				abs(current - luma[(row + 1) * columns + column])
		}
	}
	return energy
}

/** First column of the [windowColumns] wide strip with the most detail, leaning slightly towards the center. */
fun bestSpineWindow(
	energy: FloatArray,
	windowColumns: Int,
	centerBias: Float = SPINE_CENTER_BIAS,
): Int {
	val window = windowColumns.coerceIn(1, energy.size)
	var sum = 0f
	for (i in 0 until window) {
		sum += energy[i]
	}
	var bestScore = Float.NEGATIVE_INFINITY
	var bestStart = 0
	for (start in 0..energy.size - window) {
		if (start > 0) {
			sum += energy[start + window - 1] - energy[start - 1]
		}
		val offCenter = abs((start + window / 2f) / energy.size - 0.5f) * 2f
		val score = sum * (1f - centerBias * offCenter)
		if (score > bestScore) {
			bestScore = score
			bestStart = start
		}
	}
	return bestStart
}

fun spineWindowColumns(visibleWidth: Float, coverWidth: Float): Int =
	(SPINE_COLUMNS * visibleWidth / coverWidth).roundToInt().coerceIn(1, SPINE_COLUMNS)

fun leadingCalendarBlanks(firstOfMonth: LocalDate, firstDayOfWeek: DayOfWeek): Int =
	(firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
