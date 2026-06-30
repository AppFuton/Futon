package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewPropertyAnimator
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarView
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.measureHeight
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import kotlin.math.max
import com.google.android.material.R as materialR
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import io.github.landwarderer.futon.core.util.ext.resolveDp

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

private const val MAX_ITEM_COUNT = 6

class SlidingBottomNavigationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = materialR.attr.bottomNavigationStyle,
	@StyleRes defStyleRes: Int = materialR.style.Widget_Design_BottomNavigationView,
) : NavigationBarView(context, attrs, defStyleAttr, defStyleRes),
	CoordinatorLayout.AttachedBehavior {

	private var currentAnimator: ViewPropertyAnimator? = null
	private var currentState = STATE_UP
	private var behavior = HideBottomNavigationOnScrollBehavior()

	private val indicator = android.view.View(context).apply {
		layoutParams = FrameLayout.LayoutParams(
			resources.resolveDp(64),
			resources.resolveDp(32)
		).apply {
			gravity = Gravity.CENTER_VERTICAL or Gravity.START
		}
		setBackgroundResource(io.github.landwarderer.futon.R.drawable.shape_pill_indicator)
		isVisible = false
	}

	private var lastSelectedId = -1

	init {
		itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
		addView(indicator, 0)
		
		viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				val selectedId = selectedItemId
				if (selectedId != lastSelectedId) {
					if (updateIndicatorPosition(selectedId, animate = lastSelectedId != -1)) {
						lastSelectedId = selectedId
					}
				}
				return true
			}
		})
		
		addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			if (lastSelectedId != -1) {
				updateIndicatorPosition(lastSelectedId, animate = false)
			}
		}
	}

	private fun updateIndicatorPosition(itemId: Int, animate: Boolean): Boolean {
		val menuView = getChildAt(1) as? ViewGroup ?: return false
		val itemsCount = menu.size()
		for (i in 0 until itemsCount) {
			if (menu.getItem(i).itemId == itemId) {
				val itemView = menuView.getChildAt(i) ?: return false
				if (itemView.width == 0) return false

				val targetX = menuView.left + itemView.left + (itemView.width - indicator.layoutParams.width) / 2f
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

	/** From BottomNavigationView **/

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		super.onTouchEvent(event)
		// Consume all events to avoid views under the BottomNavigationView from receiving touch events.
		return true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val minHeightSpec = makeMinHeightSpec(heightMeasureSpec)
		super.onMeasure(widthMeasureSpec, minHeightSpec)
		if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
			setMeasuredDimension(
				measuredWidth,
				max(
					measuredHeight,
					suggestedMinimumHeight + paddingTop + paddingBottom,
				),
			)
		}
	}

	private fun makeMinHeightSpec(measureSpec: Int): Int {
		var minHeight = suggestedMinimumHeight
		if (MeasureSpec.getMode(measureSpec) != MeasureSpec.EXACTLY && minHeight > 0) {
			minHeight += paddingTop + paddingBottom

			return MeasureSpec.makeMeasureSpec(
				max(MeasureSpec.getSize(measureSpec), minHeight), MeasureSpec.AT_MOST,
			)
		}

		return measureSpec
	}

	override fun getMaxItemCount(): Int = MAX_ITEM_COUNT

	@SuppressLint("RestrictedApi")
	override fun createNavigationBarMenuView(context: Context) = BottomNavigationMenuView(context)

	/** End **/

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
		// Disallow translation change when state down
		if (currentState != STATE_DOWN) {
			super.setTranslationY(translationY)
		}
	}

	override fun setMinimumHeight(minHeight: Int) {
		super.setMinimumHeight(minHeight)
		getChildAt(0)?.minimumHeight = minHeight
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

		constructor(superState: Parcelable, currentState: Int, translationY: Float) : super(superState) {
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

			@Suppress("unused")
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(`in`: Parcel) = SavedState(`in`, SavedState::class.java.classLoader)

				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}
}
