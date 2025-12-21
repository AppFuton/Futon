package io.github.landwarderer.futon.favourites.ui.categories.adapter

import io.github.landwarderer.futon.core.ui.ReorderableListAdapter
import io.github.landwarderer.futon.favourites.ui.categories.FavouriteCategoriesListListener
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.adapter.ListStateHolderListener
import io.github.landwarderer.futon.list.ui.adapter.emptyStateListAD
import io.github.landwarderer.futon.list.ui.adapter.loadingStateAD
import io.github.landwarderer.futon.list.ui.model.ListModel

class CategoriesAdapter(
	onItemClickListener: FavouriteCategoriesListListener,
	listListener: ListStateHolderListener,
) : ReorderableListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CATEGORY_LARGE, categoryAD(onItemClickListener))
		addDelegate(ListItemType.NAV_ITEM, allCategoriesAD(onItemClickListener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
