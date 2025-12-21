package io.github.landwarderer.futon.reader.ui.pager.webtoon

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.R

class WebtoonGapsDecoration : RecyclerView.ItemDecoration() {

	private var gapSize = -1

	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		super.getItemOffsets(outRect, view, parent, state)
		val position = parent.getChildAdapterPosition(view)
		if (position > 0) {
			outRect.top = getGap(parent.context)
		}
	}

	private fun getGap(context: Context): Int {
		return if (gapSize == -1) {
			context.resources.getDimensionPixelOffset(R.dimen.webtoon_pages_gap).also {
				gapSize = it
			}
		} else {
			gapSize
		}
	}
}
