package io.github.landwarderer.futon.novel.data.model

import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

enum class NovelReadingMode(
	@StringRes val titleRes: Int,
) {
	CONTINUOUS_SCROLL(R.string.novel_scroll_continuous),
	PAGED(R.string.novel_scroll_paged),
}
