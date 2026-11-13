package io.github.landwarderer.futon.main.ui

import android.content.Context

private const val DOCK_CLEARANCE_DP = 136
private const val COMPACT_DOCK_CLEARANCE_DP = 108

/** Room the floating navigation dock takes above the system bars, in pixels. */
fun Context.dockClearance(isMinimalTheme: Boolean): Int {
	val clearance = if (isMinimalTheme) COMPACT_DOCK_CLEARANCE_DP else DOCK_CLEARANCE_DP
	return (clearance * resources.displayMetrics.density).toInt()
}
