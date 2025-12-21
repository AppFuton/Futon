package io.github.landwarderer.futon.core.ui.list

import android.view.View

fun interface OnListItemClickListener<I> {

	fun onItemClick(item: I, view: View)

	fun onItemLongClick(item: I, view: View): Boolean = false

	fun onItemContextClick(item: I, view: View): Boolean = onItemLongClick(item, view)
}
