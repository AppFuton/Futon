package io.github.landwarderer.futon.list.ui.adapter

import android.view.View
import io.github.landwarderer.futon.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
