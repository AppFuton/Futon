package io.github.landwarderer.futon.list.ui.size

import android.view.View
import android.widget.TextView
import io.github.landwarderer.futon.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
