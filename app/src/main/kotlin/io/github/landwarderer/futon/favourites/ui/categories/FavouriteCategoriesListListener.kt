package io.github.landwarderer.futon.favourites.ui.categories

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.core.model.FavouriteCategory
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener

interface FavouriteCategoriesListListener : OnListItemClickListener<FavouriteCategory?> {

	fun onDragHandleTouch(holder: RecyclerView.ViewHolder): Boolean

	fun onEditClick(item: FavouriteCategory, view: View)

	fun onShowAllClick(isChecked: Boolean)
}
