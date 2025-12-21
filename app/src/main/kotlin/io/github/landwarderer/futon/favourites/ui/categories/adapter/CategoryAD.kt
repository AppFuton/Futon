package io.github.landwarderer.futon.favourites.ui.categories.adapter

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.getQuantityStringSafe
import io.github.landwarderer.futon.core.util.ext.setTooltipCompat
import io.github.landwarderer.futon.databinding.ItemCategoriesAllBinding
import io.github.landwarderer.futon.databinding.ItemCategoryBinding
import io.github.landwarderer.futon.favourites.ui.categories.FavouriteCategoriesListListener
import io.github.landwarderer.futon.list.ui.model.ListModel

@SuppressLint("ClickableViewAccessibility")
fun categoryAD(
	clickListener: FavouriteCategoriesListListener,
) = adapterDelegateViewBinding<CategoryListModel, ListModel, ItemCategoryBinding>(
	{ inflater, parent -> ItemCategoryBinding.inflate(inflater, parent, false) },
) {
	val eventListener = object : OnClickListener, OnLongClickListener, OnTouchListener {
		override fun onClick(v: View) = if (v.id == R.id.imageView_edit) {
			clickListener.onEditClick(item.category, v)
		} else {
			clickListener.onItemClick(item.category, v)
		}

		override fun onLongClick(v: View) = clickListener.onItemLongClick(item.category, v)
		override fun onTouch(v: View?, event: MotionEvent): Boolean = event.actionMasked == MotionEvent.ACTION_DOWN &&
			clickListener.onDragHandleTouch(this@adapterDelegateViewBinding)
	}
	itemView.setOnClickListener(eventListener)
	itemView.setOnLongClickListener(eventListener)
	binding.imageViewEdit.setOnClickListener(eventListener)
	binding.imageViewHandle.setOnTouchListener(eventListener)

	bind {
		binding.imageViewHandle.isVisible = item.isActionsEnabled
		binding.imageViewEdit.isVisible = item.isActionsEnabled
		binding.textViewTitle.text = item.category.title
		binding.textViewSubtitle.text = if (item.mangaCount == 0) {
			getString(R.string.empty)
		} else {
			context.resources.getQuantityStringSafe(
				R.plurals.items,
				item.mangaCount,
				item.mangaCount,
			)
		}
		binding.imageViewTracker.isVisible = item.category.isTrackingEnabled
		binding.imageViewHidden.isGone = item.category.isVisibleInLibrary
		binding.coversView.setCoversAsync(item.covers)
	}
}

fun allCategoriesAD(
	clickListener: FavouriteCategoriesListListener,
) = adapterDelegateViewBinding<AllCategoriesListModel, ListModel, ItemCategoriesAllBinding>(
	{ inflater, parent -> ItemCategoriesAllBinding.inflate(inflater, parent, false) },
) {
	val eventListener = OnClickListener { v ->
		if (v.id == R.id.imageView_visible) {
			clickListener.onShowAllClick(!item.isVisible)
		} else {
			clickListener.onItemClick(null, v)
		}
	}

	itemView.setOnClickListener(eventListener)
	binding.imageViewVisible.setOnClickListener(eventListener)

	bind {
		binding.textViewSubtitle.text = if (item.mangaCount == 0) {
			getString(R.string.empty)
		} else {
			context.resources.getQuantityStringSafe(
				R.plurals.items,
				item.mangaCount,
				item.mangaCount,
			)
		}
		binding.imageViewVisible.isVisible = item.isActionsEnabled
		binding.imageViewVisible.setImageResource(
			if (item.isVisible) {
				R.drawable.ic_eye
			} else {
				R.drawable.ic_eye_off
			},
		)
		binding.imageViewVisible.setTooltipCompat(
			if (item.isVisible) {
				R.string.hide
			} else {
				R.string.show
			},
		)
		binding.coversView.setCoversAsync(item.covers)
	}
}
