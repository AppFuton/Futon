package io.github.landwarderer.futon.reader.ui.pager.doublereversed

import io.github.landwarderer.futon.reader.ui.pager.doublepage.DoubleReaderFragment

class ReversedDoubleReaderFragment : DoubleReaderFragment() {

	override val isReversed: Boolean
		get() = true

	override fun switchPageBy(delta: Int) {
		super.switchPageBy(-delta)
	}
}
