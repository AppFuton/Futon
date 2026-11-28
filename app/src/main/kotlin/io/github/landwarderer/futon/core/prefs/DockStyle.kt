package io.github.landwarderer.futon.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

/** How the bottom navigation bar looks, and where its Continue button lives. */
@Keep
enum class DockStyle(
	@StringRes val titleResId: Int,
	@StringRes val summaryResId: Int,
) {
	/** The dock is a stitched cushion, and Continue is a round button rising out of its middle. */
	TUFTED(R.string.dock_style_tufted, R.string.dock_style_tufted_summary),

	/** Continue is a round button beside the dock, and the pair stays centred. */
	BESIDE(R.string.dock_style_beside, R.string.dock_style_beside_summary),

	/** Continue joins the History item as the right half of a split pill. */
	MERGED(R.string.dock_style_merged, R.string.dock_style_merged_summary),

	/** The full-width Material bar, with Continue as a separate floating button above it. */
	CLASSIC(R.string.dock_style_classic, R.string.dock_style_classic_summary),
	;

	/** Whether Continue is part of the floating dock, rather than a floating button of its own. */
	val isDocked: Boolean
		get() = this != CLASSIC
}
