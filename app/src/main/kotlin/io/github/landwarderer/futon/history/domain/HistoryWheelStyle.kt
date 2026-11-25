package io.github.landwarderer.futon.history.domain

import androidx.annotation.Keep

/** How the History tab's Wheel view turns through recent manga. */
@Keep
enum class HistoryWheelStyle {

	/** A flat shelf turned by a click wheel below it. */
	CLICK_WHEEL,

	/** Spines fanned around the rim of a wheel. */
	ARC,
}
