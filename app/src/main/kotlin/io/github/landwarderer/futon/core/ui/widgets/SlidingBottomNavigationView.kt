package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.DockStyle
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.core.util.ext.measureHeight
import kotlin.math.max
import kotlin.math.min
import androidx.appcompat.R as appcompatR
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

private const val BESIDE_GAP_DP = 8f
private const val COMPACT_BESIDE_GAP_DP = 6f
private const val BESIDE_SIZE_DP = 56f
private const val COMPACT_BESIDE_SIZE_DP = 44f
private const val BESIDE_ICON_DP = 26f
private const val COMPACT_BESIDE_ICON_DP = 22f

private const val MERGED_SEGMENT_DP = 44f
private const val COMPACT_MERGED_SEGMENT_DP = 36f
private const val MERGED_GAP_DP = 3f
private const val MERGED_INNER_CORNER_DP = 4f
private const val MERGED_ICON_DP = 20f

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
	private var continueProgress = 0f
	private var mergedShapeRtl: Boolean? = null
	private val roundShape = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
	private val tempRect = Rect()

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
		val isBump = dockStyle == DockStyle.BUMP
		val bumpDp = if (compact) COMPACT_BUMP_DP else DOCK_BUMP_DP
		bumpHeight = if (isBump) bumpDp * density else 0f
		// Only the bump needs room above the capsule.
		setPadding(paddingLeft, px(if (isBump) bumpDp + DOCK_TOP_SPARE_DP else DOCK_TOP_SPARE_DP), paddingRight, paddingBottom)
		val isLabelled = isBump && !compact
		with(continueButton) {
			text = if (isLabelled) context.getString(R.string._continue) else null
			setIconResource(if (isBump) R.drawable.ic_history else R.drawable.ic_play)
			iconSize = when {
				isLabelled -> defaultButtonIconSize
				isBump -> px(COMPACT_BUTTON_ICON_DP)
				dockStyle == DockStyle.BESIDE -> px(if (compact) COMPACT_BESIDE_ICON_DP else BESIDE_ICON_DP)
				else -> px(MERGED_ICON_DP)
			}
			iconPadding = if (isLabelled) defaultButtonIconPadding else 0
			if (isLabelled) {
				setPaddingRelative(defaultButtonPaddingStart, 0, defaultButtonPaddingEnd, 0)
			} else {
				setPaddingRelative(0, 0, 0, 0)
			}
			// The other styles are measured to an exact size in onMeasure.
			minHeight = if (isBump) px(if (compact) COMPACT_BUTTON_HEIGHT_DP else DOCK_BUTTON_HEIGHT_DP) else 0
			minimumHeight = minHeight
			minWidth = if (isBump) px(if (compact) COMPACT_BUTTON_WIDTH_DP else DOCK_BUTTON_WIDTH_DP) else 0
			minimumWidth = minWidth
		}
		mergedShapeRtl = null
		applyButtonColors()
		continueButton.shapeAppearanceModel = roundShape
		if (!isBump) {
			dockDrawable.bumpProgress = 0f
		}
		// Leaving the split pill gives the items back their own widths.
		getChildAt(0).requestLayout()
		applyDockProgress(continueProgress)
		requestLayout()
	}

	/** Beside the dock the button takes the dock's colour; merged it takes the History highlight's, as one pill. */
	private fun applyButtonColors() {
		val (background, content) = when (dockStyle) {
			DockStyle.BUMP -> ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorPrimaryContainer)) to
				context.getThemeColor(materialR.attr.colorOnPrimaryContainer)

			DockStyle.BESIDE -> ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorSurfaceContainer)) to
				context.getThemeColor(appcompatR.attr.colorPrimary)

			DockStyle.MERGED -> (
				itemActiveIndicatorColor
					?: ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorSecondaryContainer))
				) to context.getThemeColor(materialR.attr.colorOnSecondaryContainer)
		}
		with(continueButton) {
			backgroundTintList = background
			setTextColor(content)
			iconTint = ColorStateList.valueOf(content)
		}
	}

	/** Where the Continue button lives. Experimental, switched from the main settings page. */
	var dockStyle = DockStyle.BUMP
		set(value) {
			if (field != value) {
				field = value
				applyDockStyle()
			}
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
		dockAnimator = ValueAnimator.ofFloat(continueProgress, if (isVisible) 1f else 0f).apply {
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
		continueProgress = progress
		continueButton.alpha = progress
		if (dockStyle == DockStyle.BUMP) {
			dockDrawable.bumpProgress = progress
			(getChildAt(0) as? DockMenuView)?.historyExtra = 0f
			continueButton.scaleX = 0.7f + 0.3f * progress
			continueButton.translationY = bumpHeight * (1f - progress)
		} else {
			continueButton.scaleX = 0.6f + 0.4f * progress
			continueButton.translationY = 0f
			(getChildAt(0) as? DockMenuView)?.historyExtra = if (dockStyle == DockStyle.MERGED) {
				(mergedSegmentWidth + MERGED_GAP_DP * density) * progress
			} else {
				0f
			}
			// The dock makes room for the button as it appears.
			requestLayout()
		}
		continueButton.scaleY = continueButton.scaleX
	}

	private val besideGap: Float
		get() = (if (isCompact) COMPACT_BESIDE_GAP_DP else BESIDE_GAP_DP) * density

	private val mergedSegmentWidth: Int
		get() = ((if (isCompact) COMPACT_MERGED_SEGMENT_DP else MERGED_SEGMENT_DP) * density).toInt()

	private val besideSize: Int
		get() = ((if (isCompact) COMPACT_BESIDE_SIZE_DP else BESIDE_SIZE_DP) * density).toInt()

	/** The capsule moves over by the beside button plus the gap. */
	private val besideRoom: Float
		get() = (besideSize + besideGap) * continueProgress

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		val menu = getChildAt(0)
		when (dockStyle) {
			DockStyle.BUMP -> layoutBump(menu)
			DockStyle.BESIDE -> layoutBeside(menu)
			DockStyle.MERGED -> layoutMerged(menu)
		}
	}

	private fun setFlatCapsule(menu: View) {
		dockDrawable.setCapsule(
			left = menu.left.toFloat(),
			top = menu.top.toFloat(),
			right = menu.right.toFloat(),
			bottom = menu.bottom.toFloat(),
			bump = 0f,
			halfWidth = 0f,
			radius = 0f,
		)
	}

	private fun layoutBump(menu: View) {
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

	private fun layoutBeside(menu: View) {
		setFlatCapsule(menu)
		val size = continueButton.measuredWidth
		val gap = besideGap.toInt()
		val buttonLeft = if (layoutDirection == LAYOUT_DIRECTION_RTL) menu.right + gap else menu.left - gap - size
		val buttonTop = menu.top + (menu.height - size) / 2
		continueButton.layout(buttonLeft, buttonTop, buttonLeft + size, buttonTop + size)
	}

	/**
	 * Widens the History slot by the button and narrows the others to match. The History item keeps its own width,
	 * so its indicator and the button sit side by side as one split pill, centred in the wider slot.
	 */
	@SuppressLint("RestrictedApi")
	private fun layoutMerged(menu: View) {
		setFlatCapsule(menu)
		val item = (menu as? NavigationBarMenuView)?.findItemView(R.id.nav_history)
		if (item == null || item.visibility == GONE) {
			continueButton.layout(0, 0, 0, 0)
			return
		}
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		if (mergedShapeRtl != isRtl) {
			mergedShapeRtl = isRtl
			continueButton.shapeAppearanceModel = mergedShape(isRtl)
		}
		val segment = continueButton.measuredWidth
		val gap = MERGED_GAP_DP * density
		val centerX = menu.left + item.left + item.width / 2f
		val indicatorHalf = itemActiveIndicatorWidth / 2f
		val buttonLeft = if (isRtl) {
			centerX - indicatorHalf - gap - segment
		} else {
			centerX + indicatorHalf + gap
		}.toInt()
		val icon = item.findViewById<View>(materialR.id.navigation_bar_item_icon_view)
		val centerY = if (icon != null) {
			tempRect.set(0, 0, icon.width, icon.height)
			offsetDescendantRectToMyCoords(icon, tempRect)
			tempRect.exactCenterY()
		} else {
			menu.top + itemPaddingTop + itemActiveIndicatorHeight / 2f
		}
		val buttonTop = (centerY - continueButton.measuredHeight / 2f).toInt()
		continueButton.layout(
			buttonLeft,
			buttonTop,
			buttonLeft + segment,
			buttonTop + continueButton.measuredHeight,
		)
	}

	private fun mergedShape(isRtl: Boolean): ShapeAppearanceModel {
		val inner = MERGED_INNER_CORNER_DP * density
		val outer = RelativeCornerSize(0.5f)
		return ShapeAppearanceModel.builder().apply {
			if (isRtl) {
				setTopLeftCornerSize(outer)
				setBottomLeftCornerSize(outer)
				setTopRightCornerSize(inner)
				setBottomRightCornerSize(inner)
			} else {
				setTopLeftCornerSize(inner)
				setBottomLeftCornerSize(inner)
				setTopRightCornerSize(outer)
				setBottomRightCornerSize(outer)
			}
		}.build()
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		// The empty corners around the capsule and bump belong to the content below.
		if (ev.actionMasked == MotionEvent.ACTION_DOWN && !dockDrawable.isInside(ev.x, ev.y) && !isOnContinueButton(ev)) {
			return false
		}
		return super.dispatchTouchEvent(ev)
	}

	private fun isOnContinueButton(ev: MotionEvent): Boolean = with(continueButton) {
		isVisible && isEnabled && ev.x >= left && ev.x < right && ev.y >= top && ev.y < bottom
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
			val margin = (DOCK_MARGIN_DP * density).toInt()
			val room = if (dockStyle == DockStyle.BESIDE) besideRoom else 0f
			// A full dock shrinks to make room. A centred compact dock moves over by half, so the pair stays centred.
			val start = margin + (if (isCompact) room / 2f else room).toInt()
			// Set the resolved sides too: the start margin is only resolved when the params are replaced.
			marginStart = start
			if (layoutDirection == LAYOUT_DIRECTION_RTL) {
				rightMargin = start
				leftMargin = margin
			} else {
				leftMargin = start
				rightMargin = margin
			}
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
		measureContinueButton()
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

	private fun measureContinueButton() {
		val (buttonWidth, buttonHeight) = when (dockStyle) {
			DockStyle.BUMP -> return
			DockStyle.BESIDE -> besideSize to besideSize
			DockStyle.MERGED -> mergedSegmentWidth to itemActiveIndicatorHeight
		}
		continueButton.measure(
			MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
			MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY),
		)
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
	override fun createNavigationBarMenuView(context: Context): BottomNavigationMenuView = DockMenuView(context)

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

/** The menu, able to widen the History slot so the merged Continue button fits beside its indicator. */
@SuppressLint("RestrictedApi", "ViewConstructor")
private class DockMenuView(context: Context) : BottomNavigationMenuView(context) {

	/** Extra room after the History item, in pixels, taken evenly from the other items. */
	var historyExtra = 0f
		set(value) {
			if (field != value) {
				field = value
				requestLayout()
			}
		}

	private val visibleItems: List<View>
		get() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		val items = visibleItems
		val history = findItemView(R.id.nav_history)
		if (historyExtra <= 0f || items.size < 2 || items.none { it === history }) {
			return
		}
		val base = items.sumOf { it.measuredWidth } / items.size
		val narrowed = (base - historyExtra / (items.size - 1)).toInt().coerceAtLeast(0)
		for (child in items) {
			if (child !== history) {
				child.measure(
					MeasureSpec.makeMeasureSpec(narrowed, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY),
				)
			}
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val items = visibleItems
		val history = findItemView(R.id.nav_history)
		if (historyExtra <= 0f || items.size < 2 || items.none { it === history }) {
			super.onLayout(changed, left, top, right, bottom)
			return
		}
		val width = right - left
		val height = bottom - top
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		var used = 0f
		for (child in items) {
			val childWidth = child.measuredWidth
			val start = used.toInt()
			if (isRtl) {
				child.layout(width - start - childWidth, 0, width - start, height)
			} else {
				child.layout(start, 0, start + childWidth, height)
			}
			used += childWidth + if (child === history) historyExtra else 0f
		}
	}
}
