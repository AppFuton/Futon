package io.github.landwarderer.futon.explore.ui.model

import io.github.landwarderer.futon.list.ui.model.ListModel

data class ExploreButtons(
	val isRandomLoading: Boolean,
	val activePresetName: String? = null,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ExploreButtons
	}
}
