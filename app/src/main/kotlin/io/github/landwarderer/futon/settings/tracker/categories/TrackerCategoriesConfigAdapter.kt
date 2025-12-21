package io.github.landwarderer.futon.settings.tracker.categories

import io.github.landwarderer.futon.core.model.FavouriteCategory
import io.github.landwarderer.futon.core.ui.BaseListAdapter
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener

class TrackerCategoriesConfigAdapter(
	listener: OnListItemClickListener<FavouriteCategory>,
) : BaseListAdapter<FavouriteCategory>() {

	init {
		delegatesManager.addDelegate(trackerCategoryAD(listener))
	}
}
