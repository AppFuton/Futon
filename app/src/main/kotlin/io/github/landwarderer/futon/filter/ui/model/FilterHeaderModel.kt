package io.github.landwarderer.futon.filter.ui.model

import io.github.landwarderer.futon.core.ui.widgets.ChipsView
import io.github.landwarderer.futon.parsers.model.SortOrder

data class FilterHeaderModel(
	val chips: Collection<ChipsView.ChipModel>,
	val sortOrder: SortOrder?,
	val isFilterApplied: Boolean,
) {

	val textSummary: String
		get() = chips.mapNotNull { if (it.isChecked) it.title else null }.joinToString()
}
