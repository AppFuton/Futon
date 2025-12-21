package io.github.landwarderer.futon.settings.nav.model

import androidx.annotation.StringRes
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
