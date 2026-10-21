package io.github.landwarderer.futon.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

@Keep
enum class HomeBackgroundFont(
	@StringRes val titleRes: Int,
	val fontAssetPath: String?,
) {
	LIGHT_THIN(R.string.home_background_font_light_thin, "fonts/MojiwTegami-Standard.ttf"),
	BOLD(R.string.home_background_font_bold, "fonts/ShimbashiGothic-Regular.otf"),
	EXTRA_BOLD(R.string.home_background_font_extra_bold, "fonts/GenEiKiwamiGo.ttf"),
	NONE(R.string.home_background_font_none, null),
	;

	companion object {
		val DEFAULT = BOLD
	}
}
