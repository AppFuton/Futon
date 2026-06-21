package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.measureHeight

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

class PillNavigationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), CoordinatorLayout.AttachedBehavior {

	private var currentAnimator: ViewPropertyAnimator? = null
	private var currentState = STATE_UP
	private var behavior = HideBottomNavigationOnScrollBehavior()

	private val indicator: android.view.View
	val bottomNav: BottomNavigationView
	val fab: ImageButton

	private var lastSelectedId = -1

	init {
		LayoutInflater.from(context).inflate(R.layout.layout_pill_navigation, this, true)
		indicator = findViewById(R.id.pill_indicator)
		bottomNav = findViewById(R.id.pill_bottom_nav)
		fab = findViewById(R.id.pill_fab)

		bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
		bottomNav.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				val selectedId = bottomNav.selectedItemId
				if (selectedId != lastSelectedId) {
					if (updateIndicatorPosition(selectedId, animate = lastSelectedId != -1)) {
						lastSelectedId = selectedId
					}
				}
				return true
			}
		})
		
		bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			if (lastSelectedId != -1) {
				updateIndicatorPosition(lastSelectedId, animate = false)
			}
		}
	}

	private fun updateIndicatorPosition(itemId: Int, animate: Boolean): Boolean {
		val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return false
		val itemsCount = bottomNav.menu.size()
		for (i in 0 until itemsCount) {
			if (bottomNav.menu.getItem(i).itemId == itemId) {
				val itemView = menuView.getChildAt(i) ?: return false
				if (itemView.width == 0) return false

				val targetX = menuView.left + itemView.left + (itemView.width - indicator.width) / 2f
				if (animate && indicator.isVisible) {
					indicator.animate()
						.translationX(targetX)
						.setInterpolator(FastOutLinearInInterpolator())
						.setDuration(225L)
						.start()
				} else {
					indicator.animate().cancel()
					indicator.translationX = targetX
					indicator.isVisible = true
				}
				return true
			}
		}
		return false
	}

	var isPinned: Boolean
		get() = behavior.isPinned
		set(value) {
			behavior.isPinned = value
			if (value) {
				translationY = 0f
			}
		}

	val isShownOrShowing: Boolean
		get() = isVisible && currentState == STATE_UP

	override fun getBehavior(): CoordinatorLayout.Behavior<*> {
		return behavior
	}

	override fun onSaveInstanceState(): Parcelable {
		val superState = super.onSaveInstanceState()
		return SavedState(superState, currentState, translationY)
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state is SavedState) {
			super.onRestoreInstanceState(state.superState)
			super.setTranslationY(state.translationY)
			currentState = state.currentState
		} else {
			super.onRestoreInstanceState(state)
		}
	}

	override fun setTranslationY(translationY: Float) {
		if (currentState != STATE_DOWN) {
			super.setTranslationY(translationY)
		}
	}

	fun show() {
		if (currentState == STATE_UP) {
			return
		}
		currentAnimator?.cancel()
		clearAnimation()

		currentState = STATE_UP
		animateTranslation(
			0F,
			SLIDE_UP_ANIMATION_DURATION,
			LinearOutSlowInInterpolator(),
		)
	}

	fun hide() {
		if (currentState == STATE_DOWN) {
			return
		}
		currentAnimator?.cancel()
		clearAnimation()

		currentState = STATE_DOWN
		val target = measureHeight()
		if (target == 0) {
			return
		}
		animateTranslation(
			target.toFloat(),
			SLIDE_DOWN_ANIMATION_DURATION,
			FastOutLinearInInterpolator(),
		)
	}

	fun showOrHide(show: Boolean) {
		if (show) {
			show()
		} else {
			hide()
		}
	}

	private fun animateTranslation(targetY: Float, duration: Long, interpolator: TimeInterpolator) {
		currentAnimator = animate()
			.translationY(targetY)
			.setInterpolator(interpolator)
			.setDuration(duration)
			.applySystemAnimatorScale(context)
			.setListener(
				object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						currentAnimator = null
						postInvalidate()
					}
				},
			)
	}

	internal class SavedState : AbsSavedState {

		var currentState = STATE_UP
		var translationY = 0F

		constructor(superState: Parcelable?, currentState: Int, translationY: Float) : super(superState!!) {
			this.currentState = currentState
			this.translationY = translationY
		}

		constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
			currentState = source.readInt()
			translationY = source.readFloat()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(currentState)
			out.writeFloat(translationY)
		}

		companion object {
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(`in`: Parcel) = SavedState(`in`, SavedState::class.java.classLoader)
				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}
}
