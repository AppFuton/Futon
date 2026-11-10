package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.core.util.ext.measureHeight
import kotlin.math.max
import kotlin.math.min
import com.google.android.material.R as materialR

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

private const val MAX_ITEM_COUNT = 6

private const val DOCK_BUMP_DP = 47f
private const val DOCK_MARGIN_DP = 16f
private const val DOCK_BOTTOM_MARGIN_DP = 8f
private const val DOCK_BUTTON_HEIGHT_DP = 44f
private const val DOCK_BUTTON_WIDTH_DP = 136f
private const val DOCK_BUTTON_GAP_DP = 9f
private const val DOCK_TOP_SPARE_DP = 4f
private const val DOCK_ANIMATION_DURATION = 320L

private const val COMPACT_BUMP_DP = 42f
private const val COMPACT_HEIGHT_DP = 56f
private const val COMPACT_ITEM_WIDTH_DP = 64f
private const val COMPACT_ITEM_PADDING_DP = 10f
private const val COMPACT_ICON_DP = 24f
private const val COMPACT_INDICATOR_WIDTH_DP = 56f
private const val COMPACT_INDICATOR_HEIGHT_DP = 36f
private const val COMPACT_BUTTON_HEIGHT_DP = 36f
private const val COMPACT_BUTTON_WIDTH_DP = 60f
private const val COMPACT_BUTTON_ICON_DP = 20f
private const val COMPACT_BUTTON_GAP_DP = 5f

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

	private val density = resources.displayMetrics.density
	private var bumpHeight = DOCK_BUMP_DP * density
	private val defaultItemPaddingTop = itemPaddingTop
	private val defaultItemPaddingBottom = itemPaddingBottom
	private val defaultIndicatorWidth = itemActiveIndicatorWidth
	private val defaultIndicatorHeight = itemActiveIndicatorHeight
	private val defaultIconSize = itemIconSize
	private var requestedMinimumHeight = minimumHeight
	private val dockDrawable = DockBackgroundDrawable(
		color = context.getThemeColor(materialR.attr.colorSurfaceContainer),
	)
	private var defaultButtonIconSize = 0
	private var defaultButtonIconPadding = 0
	private var defaultButtonPaddingStart = 0
	private var defaultButtonPaddingEnd = 0
	private var requestedLabelMode = LABEL_VISIBILITY_SELECTED
	private var isContinueShown = false
	private var dockAnimator: ValueAnimator? = null

	/** Rises out of the dock while reading can be resumed from the current screen. */
	val continueButton = MaterialButton(context).apply {
		id = R.id.fab
		setText(R.string._continue)
		setIconResource(R.drawable.ic_history)
		val onContainer = context.getThemeColor(materialR.attr.colorOnPrimaryContainer)
		backgroundTintList = ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorPrimaryContainer))
		setTextColor(onContainer)
		iconTint = ColorStateList.valueOf(onContainer)
		insetTop = 0
		insetBottom = 0
		minHeight = (DOCK_BUTTON_HEIGHT_DP * density).toInt()
		minimumHeight = minHeight
		minWidth = (DOCK_BUTTON_WIDTH_DP * density).toInt()
		minimumWidth = minWidth
		contentDescription = text
		iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
		isVisible = false
		alpha = 0f
	}

	init {
		defaultButtonIconSize = continueButton.iconSize
		defaultButtonIconPadding = continueButton.iconPadding
		defaultButtonPaddingStart = continueButton.paddingStart
		defaultButtonPaddingEnd = continueButton.paddingEnd
		background = dockDrawable
		elevation = 0f
		// The outline is expensive to rasterize, and content scrolls behind it on every frame.
		setLayerType(LAYER_TYPE_HARDWARE, null)
		// The Continue button sits in the top padding, above the capsule.
		clipToPadding = false
		labelVisibilityMode = LABEL_VISIBILITY_SELECTED
		setPadding(paddingLeft, ((DOCK_BUMP_DP + DOCK_TOP_SPARE_DP) * density).toInt(), paddingRight, paddingBottom)
		val margin = (DOCK_MARGIN_DP * density).toInt()
		(getChildAt(0).layoutParams as FrameLayout.LayoutParams).run {
			marginStart = margin
			marginEnd = margin
			bottomMargin = (DOCK_BOTTOM_MARGIN_DP * density).toInt()
		}
		addView(
			continueButton,
			FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
		)
	}

	// The label preference is applied from outside and must not bring labels back to the compact dock.
	override fun setLabelVisibilityMode(labelVisibilityMode: Int) {
		requestedLabelMode = labelVisibilityMode
		super.setLabelVisibilityMode(if (isCompact) LABEL_VISIBILITY_UNLABELED else labelVisibilityMode)
	}

	/** A much smaller dock that only shows icons. */
	var isCompact = false
		set(value) {
			if (field != value) {
				field = value
				applyDockStyle()
			}
		}

	private fun applyDockStyle() {
		val compact = isCompact
		fun px(dp: Float) = (dp * density).toInt()
		super.setLabelVisibilityMode(if (compact) LABEL_VISIBILITY_UNLABELED else requestedLabelMode)
		itemPaddingTop = if (compact) px(COMPACT_ITEM_PADDING_DP) else defaultItemPaddingTop
		itemPaddingBottom = if (compact) px(COMPACT_ITEM_PADDING_DP) else defaultItemPaddingBottom
		itemActiveIndicatorWidth = if (compact) px(COMPACT_INDICATOR_WIDTH_DP) else defaultIndicatorWidth
		itemActiveIndicatorHeight = if (compact) px(COMPACT_INDICATOR_HEIGHT_DP) else defaultIndicatorHeight
		itemIconSize = if (compact) px(COMPACT_ICON_DP) else defaultIconSize
		applyMinimumHeight(if (compact) px(COMPACT_HEIGHT_DP) else requestedMinimumHeight)
		val bumpDp = if (compact) COMPACT_BUMP_DP else DOCK_BUMP_DP
		bumpHeight = bumpDp * density
		setPadding(paddingLeft, px(bumpDp + DOCK_TOP_SPARE_DP), paddingRight, paddingBottom)
		with(continueButton) {
			text = if (compact) null else context.getString(R.string._continue)
			iconSize = if (compact) px(COMPACT_BUTTON_ICON_DP) else defaultButtonIconSize
			iconPadding = if (compact) 0 else defaultButtonIconPadding
			if (compact) {
				setPaddingRelative(0, 0, 0, 0)
			} else {
				setPaddingRelative(defaultButtonPaddingStart, 0, defaultButtonPaddingEnd, 0)
			}
			minHeight = px(if (compact) COMPACT_BUTTON_HEIGHT_DP else DOCK_BUTTON_HEIGHT_DP)
			minimumHeight = minHeight
			minWidth = px(if (compact) COMPACT_BUTTON_WIDTH_DP else DOCK_BUTTON_WIDTH_DP)
			minimumWidth = minWidth
		}
		requestLayout()
	}

	fun setContinueVisible(isVisible: Boolean) {
		if (isContinueShown == isVisible) {
			return
		}
		isContinueShown = isVisible
		dockAnimator?.cancel()
		continueButton.isEnabled = isVisible
		if (isVisible) {
			continueButton.isVisible = true
		}
		dockAnimator = ValueAnimator.ofFloat(dockDrawable.bumpProgress, if (isVisible) 1f else 0f).apply {
			duration = DOCK_ANIMATION_DURATION
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener { applyDockProgress(it.animatedValue as Float) }
			addListener(
				object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (!isContinueShown) {
							continueButton.isVisible = false
						}
					}
				},
			)
			start()
		}
	}

	private fun applyDockProgress(progress: Float) {
		dockDrawable.bumpProgress = progress
		continueButton.alpha = progress
		continueButton.scaleX = 0.7f + 0.3f * progress
		continueButton.scaleY = continueButton.scaleX
		continueButton.translationY = bumpHeight * (1f - progress)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		val menu = getChildAt(0)
		val gap = (if (isCompact) COMPACT_BUTTON_GAP_DP else DOCK_BUTTON_GAP_DP) * density
		// Anything drawn above the top of this view would be cut off flat.
		val bump = min(bumpHeight, menu.top.toFloat())
		dockDrawable.setCapsule(
			left = menu.left.toFloat(),
			top = menu.top.toFloat(),
			right = menu.right.toFloat(),
			bottom = menu.bottom.toFloat(),
			bump = bump,
			halfWidth = continueButton.measuredWidth / 2f + gap,
			radius = continueButton.measuredHeight / 2f + gap,
		)
		val buttonLeft = (width - continueButton.measuredWidth) / 2
		val buttonTop = (menu.top - bump + gap).toInt()
		continueButton.layout(
			buttonLeft,
			buttonTop,
			buttonLeft + continueButton.measuredWidth,
			buttonTop + continueButton.measuredHeight,
		)
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		// The empty corners around the capsule and bump belong to the content below.
		if (ev.actionMasked == MotionEvent.ACTION_DOWN && !dockDrawable.isInside(ev.x, ev.y)) {
			return false
		}
		return super.dispatchTouchEvent(ev)
	}

	var isPinned: Boolean
		get() = behavior.isPinned
		set(value) {
			behavior.isPinned = value
			if (value) {
				translationX = 0f
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
		(getChildAt(0).layoutParams as FrameLayout.LayoutParams).run {
			if (isCompact && menu.size > 0) {
				val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - leftMargin - rightMargin
				width = min(available, (menu.size * COMPACT_ITEM_WIDTH_DP * density).toInt())
				gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
			} else {
				width = FrameLayout.LayoutParams.MATCH_PARENT
				gravity = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
			}
		}
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
		requestedMinimumHeight = minHeight
		applyMinimumHeight(if (isCompact) (COMPACT_HEIGHT_DP * density).toInt() else minHeight)
	}

	private fun applyMinimumHeight(value: Int) {
		super.setMinimumHeight(value)
		getChildAt(0)?.minimumHeight = value
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
