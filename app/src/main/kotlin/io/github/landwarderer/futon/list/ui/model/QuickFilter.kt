package io.github.landwarderer.futon.list.ui.model

import io.github.landwarderer.futon.core.ui.widgets.ChipsView
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback

data class QuickFilter(
	val items: List<ChipsView.ChipModel>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is QuickFilter

	override fun getChangePayload(previousState: ListModel) = ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
}
