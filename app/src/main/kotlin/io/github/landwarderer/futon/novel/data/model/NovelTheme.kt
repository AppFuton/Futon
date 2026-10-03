package io.github.landwarderer.futon.novel.data.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

enum class NovelTheme(
	@StringRes val titleRes: Int,
	@ColorInt val backgroundColor: Int,
	@ColorInt val textColor: Int,
) {
	DARK(R.string.novel_theme_dark, Color.parseColor("#141414"), Color.parseColor("#E0E0E0")),
	LIGHT(R.string.novel_theme_light, Color.parseColor("#FFFFFF"), Color.parseColor("#212121")),
	OLED(R.string.novel_theme_oled, Color.parseColor("#000000"), Color.parseColor("#FFFFFF")),
	SEPIA(R.string.novel_theme_sepia, Color.parseColor("#FBF0D9"), Color.parseColor("#5F4B32")),
	CUSTOM(R.string.novel_theme_custom, Color.parseColor("#141414"), Color.parseColor("#E0E0E0")),
}
