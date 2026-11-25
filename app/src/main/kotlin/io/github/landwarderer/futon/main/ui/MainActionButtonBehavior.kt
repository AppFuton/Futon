package io.github.landwarderer.futon.main.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import io.github.landwarderer.futon.core.ui.util.ShrinkOnScrollBehavior
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView

class MainActionButtonBehavior : ShrinkOnScrollBehavior {

	constructor() : super()
	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

	override fun layoutDependsOn(
		parent: CoordinatorLayout,
		child: ExtendedFloatingActionButton,
		dependency: View
	): Boolean {
		return dependency is SlidingBottomNavigationView || super.layoutDependsOn(parent, child, dependency)
	}

	override fun onDependentViewChanged(
		parent: CoordinatorLayout,
		child: ExtendedFloatingActionButton,
		dependency: View
	): Boolean {
		val bottom = child.bottom
		val bottomLine = parent.height
		return if (bottom > bottomLine) {
			ViewCompat.offsetTopAndBottom(child, bottomLine - bottom)
			true
		} else {
			false
		}
	}
}
