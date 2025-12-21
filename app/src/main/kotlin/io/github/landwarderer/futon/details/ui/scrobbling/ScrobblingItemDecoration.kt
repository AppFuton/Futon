package io.github.landwarderer.futon.details.ui.scrobbling

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.R

class ScrobblingItemDecoration : RecyclerView.ItemDecoration() {

	private var spacing: Int = -1

	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		if (spacing == -1) {
			spacing = parent.context.resources.getDimensionPixelOffset(R.dimen.scrobbling_list_spacing)
		}
		outRect.set(0, spacing, 0, 0)
	}
}
