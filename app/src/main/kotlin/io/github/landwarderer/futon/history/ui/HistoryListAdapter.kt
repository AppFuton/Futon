package io.github.landwarderer.futon.history.ui

import android.content.Context
import io.github.landwarderer.futon.core.ui.list.fastscroll.FastScroller
import io.github.landwarderer.futon.list.ui.adapter.MangaListAdapter
import io.github.landwarderer.futon.list.ui.adapter.MangaListListener
import io.github.landwarderer.futon.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
