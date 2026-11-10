package io.github.landwarderer.futon.history.ui.views

import android.view.View
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import java.util.concurrent.TimeUnit

interface HistoryModeView {

	/** Views that do not scroll cannot collapse the app bar, so they must fit above it being expanded. */
	val scrollsVertically: Boolean

	fun setEntries(entries: List<HistoryEntry>, isStatsEnabled: Boolean)

	/** Room to leave below the content for the navigation dock and system bars. */
	fun setBottomInset(inset: Int)
}

internal fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun HistoryEntry.readingTimeSince(from: Long): Long =
	sessions.sumOf { if (it.startedAt >= from) it.duration else 0L }

internal fun lastWeekStart(now: Long = System.currentTimeMillis()): Long = now - TimeUnit.DAYS.toMillis(7)
