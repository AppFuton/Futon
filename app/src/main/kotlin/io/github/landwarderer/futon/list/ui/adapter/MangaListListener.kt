package io.github.landwarderer.futon.list.ui.adapter

import android.view.View
import io.github.landwarderer.futon.core.ui.widgets.TipView

interface MangaListListener : MangaDetailsClickListener, ListStateHolderListener, ListHeaderClickListener,
	TipView.OnButtonClickListener, QuickFilterClickListener {

	fun onFilterClick(view: View?)
}
