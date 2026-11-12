package io.github.landwarderer.futon.core.prefs

import androidx.annotation.Keep

/** Where the Continue button lives in the bottom dock. Experimental, while the options are compared. */
@Keep
enum class DockStyle {
	/** Continue rises out of a bump in the top of the dock. */
	BUMP,

	/** Continue is a round button beside the dock, and the pair stays centred. */
	BESIDE,

	/** Continue joins the History item as the right half of a split pill. */
	MERGED,
}
