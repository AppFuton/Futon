package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.measureHeight
import kotlin.math.abs

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

private const val INDICATOR_SLIDE_DURATION = 300L
private const val INDICATOR_STRETCH_PEAK = 1.4f
private const val INDICATOR_SQUASH_PEAK = 0.85f
private const val ICON_BOUNCE_SCALE = 1.2f
private const val ICON_BOUNCE_DURATION = 300L

class PillNavigationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), CoordinatorLayout.AttachedBehavior {

	private var currentAnimator: ViewPropertyAnimator? = null
	private var currentState = STATE_UP
	private var behavior = HideBottomNavigationOnScrollBehavior()

	private val indicator: View
	val bottomNav: BottomNavigationView
	val fab: ImageButton

	private var lastSelectedId = -1
	private var indicatorAnimatorSet: AnimatorSet? = null
	private var previousSelectedIndex = -1

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
					animateIndicatorTo(targetX, i, menuView)
				} else {
					cancelIndicatorAnimation()
					indicator.translationX = targetX
					indicator.scaleX = 1f
					indicator.scaleY = 1f
					indicator.isVisible = true
				}
				previousSelectedIndex = i
				return true
			}
		}
		return false
	}

	private fun animateIndicatorTo(targetX: Float, targetIndex: Int, menuView: ViewGroup) {
		cancelIndicatorAnimation()

		val startX = indicator.translationX
		val distance = abs(targetX - startX)
		val indicatorWidth = indicator.width.toFloat()

		// Scale stretch amount based on travel distance relative to indicator size
		val distanceRatio = (distance / indicatorWidth).coerceIn(0f, 3f)
		val stretchAmount = 1f + (INDICATOR_STRETCH_PEAK - 1f) * (distanceRatio / 3f).coerceAtLeast(0.3f)
		val squashAmount = 1f - (1f - INDICATOR_SQUASH_PEAK) * (distanceRatio / 3f).coerceAtLeast(0.3f)

		// Phase 1: Stretch out and start moving
		val stretchScaleX = ObjectAnimator.ofFloat(indicator, View.SCALE_X, 1f, stretchAmount).apply {
			duration = INDICATOR_SLIDE_DURATION * 2 / 5
			interpolator = FastOutSlowInInterpolator()
		}
		val squashScaleY = ObjectAnimator.ofFloat(indicator, View.SCALE_Y, 1f, squashAmount).apply {
			duration = INDICATOR_SLIDE_DURATION * 2 / 5
			interpolator = FastOutSlowInInterpolator()
		}

		// Phase 2: Move to target position (full duration)
		val slideX = ObjectAnimator.ofFloat(indicator, View.TRANSLATION_X, startX, targetX).apply {
			duration = INDICATOR_SLIDE_DURATION
			interpolator = FastOutSlowInInterpolator()
		}

		// Phase 3: Snap back to normal scale with overshoot (starts at 50% of slide)
		val settleScaleX = ObjectAnimator.ofFloat(indicator, View.SCALE_X, stretchAmount, 1f).apply {
			duration = INDICATOR_SLIDE_DURATION * 3 / 5
			startDelay = INDICATOR_SLIDE_DURATION * 2 / 5
			interpolator = OvershootInterpolator(2f)
		}
		val settleScaleY = ObjectAnimator.ofFloat(indicator, View.SCALE_Y, squashAmount, 1f).apply {
			duration = INDICATOR_SLIDE_DURATION * 3 / 5
			startDelay = INDICATOR_SLIDE_DURATION * 2 / 5
			interpolator = OvershootInterpolator(2f)
		}

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(stretchScaleX, squashScaleY, slideX, settleScaleX, settleScaleY)
		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				indicatorAnimatorSet = null
				// Ensure clean state
				indicator.scaleX = 1f
				indicator.scaleY = 1f
			}

			override fun onAnimationCancel(animation: Animator) {
				indicatorAnimatorSet = null
			}
		})

		indicatorAnimatorSet = animatorSet
		animatorSet.start()

		// Bounce the target icon
		bounceIcon(targetIndex, menuView)
	}

	private fun bounceIcon(targetIndex: Int, menuView: ViewGroup) {
		val targetItemView = menuView.getChildAt(targetIndex) ?: return
		// Find the icon view inside the menu item (typically the first ImageView)
		val iconView = findIconView(targetItemView) ?: return

		iconView.animate().cancel()
		iconView.animate()
			.scaleX(ICON_BOUNCE_SCALE)
			.scaleY(ICON_BOUNCE_SCALE)
			.setDuration(ICON_BOUNCE_DURATION / 2)
			.setInterpolator(FastOutSlowInInterpolator())
			.withEndAction {
				iconView.animate()
					.scaleX(1f)
					.scaleY(1f)
					.setDuration(ICON_BOUNCE_DURATION / 2)
					.setInterpolator(OvershootInterpolator(2f))
					.start()
			}
			.start()
	}

	private fun findIconView(itemView: View): View? {
		if (itemView !is ViewGroup) return null
		// Navigate into the BottomNavigationItemView to find the icon
		for (i in 0 until itemView.childCount) {
			val child = itemView.getChildAt(i)
			if (child is android.widget.ImageView) {
				return child
			}
			// Check nested ViewGroups (icon container)
			if (child is ViewGroup) {
				for (j in 0 until child.childCount) {
					val nested = child.getChildAt(j)
					if (nested is android.widget.ImageView) {
						return nested
					}
				}
			}
		}
		return null
	}

	private fun cancelIndicatorAnimation() {
		indicatorAnimatorSet?.cancel()
		indicatorAnimatorSet = null
		indicator.animate().cancel()
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

