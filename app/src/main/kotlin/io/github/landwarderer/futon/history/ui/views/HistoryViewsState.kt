package io.github.landwarderer.futon.history.ui.views

import io.github.landwarderer.futon.history.domain.activityScore
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One manga's reading on one day of the calendar. */
data class DayRead(
	val entry: HistoryEntry,
	val startedAt: Long,
	val duration: Long,
	val pages: Int,
	/** False for reading from before statistics were kept, placed by when the manga was last read. */
	val isTimed: Boolean,
)

/**
 * Everything the history views show, prepared away from the main thread.
 * [ranked] orders [entries] by recent reading activity, and [days] groups the reading by calendar day.
 */
class HistoryViewsState(
	val entries: List<HistoryEntry>,
	val isStatsEnabled: Boolean,
	val ranked: List<HistoryEntry>,
	val days: Map<LocalDate, List<DayRead>>,
)

fun HistoryViewsState(
	entries: List<HistoryEntry>,
	isStatsEnabled: Boolean,
	now: Long = System.currentTimeMillis(),
	zone: ZoneId = ZoneId.systemDefault(),
): HistoryViewsState = HistoryViewsState(
	entries = entries,
	isStatsEnabled = isStatsEnabled,
	ranked = entries.sortedByDescending { activityScore(it.updatedAt, it.sessions, now) },
	days = groupReadsByDay(entries, isStatsEnabled, zone),
)

private fun groupReadsByDay(
	entries: List<HistoryEntry>,
	useSessions: Boolean,
	zone: ZoneId,
): Map<LocalDate, List<DayRead>> {
	fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
	val result = HashMap<LocalDate, MutableList<DayRead>>()
	for (entry in entries) {
		if (useSessions && entry.sessions.isNotEmpty()) {
			entry.sessions.groupBy { it.startedAt.toLocalDate() }.forEach { (date, sessions) ->
				result.getOrPut(date) { ArrayList() } += DayRead(
					entry = entry,
					startedAt = sessions.minOf { it.startedAt },
					duration = sessions.sumOf { it.duration },
					pages = sessions.sumOf { it.pages },
					isTimed = true,
				)
			}
		} else {
			// Without sessions, by choice or because it was read before statistics were on, the last read still counts.
			result.getOrPut(entry.updatedAt.toLocalDate()) { ArrayList() } += DayRead(
				entry = entry,
				startedAt = entry.updatedAt,
				duration = 0L,
				pages = 0,
				isTimed = false,
			)
		}
	}
	val order = compareByDescending<DayRead> { it.duration }.thenByDescending { it.startedAt }
	result.values.forEach { it.sortWith(order) }
	return result
}
