package io.github.landwarderer.futon.explore.data

import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

enum class SourcesSortOrder(
	@StringRes val titleResId: Int,
) {
	ALPHABETIC(R.string.by_name),
	POPULARITY(R.string.popular),
	MANUAL(R.string.manual),
	LAST_USED(R.string.last_used),
}
