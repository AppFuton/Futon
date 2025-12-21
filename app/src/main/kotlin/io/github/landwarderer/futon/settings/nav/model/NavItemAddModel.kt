package io.github.landwarderer.futon.settings.nav.model

import io.github.landwarderer.futon.list.ui.model.ListModel

data class NavItemAddModel(
	val canAdd: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is NavItemAddModel
}
