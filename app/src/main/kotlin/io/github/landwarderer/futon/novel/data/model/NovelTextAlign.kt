package io.github.landwarderer.futon.novel.data.model

import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

enum class NovelTextAlign(
	@StringRes val titleRes: Int,
	val cssValue: String,
) {
	LEFT(R.string.novel_align_left, "left"),
	JUSTIFY(R.string.novel_align_justify, "justify"),
}
