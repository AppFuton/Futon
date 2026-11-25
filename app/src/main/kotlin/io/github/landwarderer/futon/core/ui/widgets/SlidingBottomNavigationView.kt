package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
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
import androidx.core.graphics.ColorUtils
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

private const val DOCK_MARGIN_DP = 16f
private const val DOCK_BOTTOM_MARGIN_DP = 8f
private const val DOCK_TOP_SPARE_DP = 4f

/** Room inside each rounded end of the dock, so the end items' highlights stay clear of the curve. */
private const val DOCK_END_INSET_DP = 10f
private const val COMPACT_DOCK_END_INSET_DP = 6f
private const val DOCK_ANIMATION_DURATION = 320L

private const val COMPACT_HEIGHT_DP = 56f
private const val COMPACT_ITEM_WIDTH_DP = 64f
private const val COMPACT_ITEM_PADDING_DP = 10f
private const val COMPACT_ICON_DP = 24f
private const val COMPACT_INDICATOR_WIDTH_DP = 56f
private const val COMPACT_INDICATOR_HEIGHT_DP = 36f

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

private const val TUFT_SIZE_DP = 44f
private const val COMPACT_TUFT_SIZE_DP = 36f
private const val TUFT_RISE_DP = 14f
private const val COMPACT_TUFT_RISE_DP = 12f
private const val TUFT_HALO_DP = 4f
private const val COMPACT_TUFT_HALO_DP = 3f

/** The gap the items part by, just enough to clear the button's seat between their highlights. */
private const val TUFT_GAP_DP = 42f
private const val TUFT_ICON_DP = 18f
private const val COMPACT_TUFT_ICON_DP = 16f
private const val TUFT_STITCH_INSET_DP = 4f
private const val TUFT_STITCH_WIDTH_DP = 1.2f
private const val TUFT_STITCH_DASH_DP = 3f
private const val TUFT_STITCH_ALPHA = 90

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
	private val defaultItemPaddingTop = itemPaddingTop
	private val defaultItemPaddingBottom = itemPaddingBottom
	private val defaultIndicatorWidth = itemActiveIndicatorWidth
	private val defaultIndicatorHeight = itemActiveIndicatorHeight
	private val defaultIconSize = itemIconSize
	private var requestedMinimumHeight = minimumHeight
	private val dockDrawable = DockBackgroundDrawable(
		color = context.getThemeColor(materialR.attr.colorSurfaceContainer),
	)
	// The Material bar's own look, kept for the Classic style.
	private val classicBackground: Drawable? = background
	private val classicElevation = elevation
	private var requestedLabelMode = LABEL_VISIBILITY_SELECTED
	private var isContinueShown = false
	private var dockAnimator: ValueAnimator? = null
	private var continueProgress = 0f
	private var mergedShapeRtl: Boolean? = null
	private val roundShape = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
	private val tempRect = Rect()

	/** Rises out of the dock while reading can be resumed from the current screen. */
	val continueButton = MaterialButton(context).apply {
		id = R.id.dock_continue
		contentDescription = context.getString(R.string._continue)
		setIconResource(R.drawable.ic_play)
		iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
		iconPadding = 0
		setPaddingRelative(0, 0, 0, 0)
		insetTop = 0
		insetBottom = 0
		// Each style measures it to an exact size in onMeasure.
		minHeight = 0
		minimumHeight = 0
		minWidth = 0
		minimumWidth = 0
		isVisible = false
		alpha = 0f
	}

	/** A much smaller dock that only shows icons. The Classic bar has no smaller form, so it ignores this. */
	var isCompact = false
		get() = field && dockStyle.isDocked
		set(value) {
			if (field != value) {
				field = value
				applyDockStyle()
			}
		}

	/** How the bar looks, and where the Continue button lives. Classic leaves Continue to the activity. */
	var dockStyle = DockStyle.TUFTED
		set(value) {
			if (field != value) {
				field = value
				if (!value.isDocked) {
					setContinueVisible(false)
				}
				applyDockStyle()
			}
		}

	init {
		elevation = 0f
		// The outline is expensive to rasterize, and content scrolls behind it on every frame.
		setLayerType(LAYER_TYPE_HARDWARE, null)
		// The Continue button sits in the top padding, above the capsule.
		clipToPadding = false
		labelVisibilityMode = LABEL_VISIBILITY_SELECTED
		addView(
			continueButton,
			FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
		)
		applyDockStyle()
	}

	// The label preference is applied from outside and must not bring labels back to the compact dock.
	override fun setLabelVisibilityMode(labelVisibilityMode: Int) {
		requestedLabelMode = labelVisibilityMode
		super.setLabelVisibilityMode(if (isCompact) LABEL_VISIBILITY_UNLABELED else labelVisibilityMode)
	}

	private fun applyDockStyle() {
		val compact = isCompact
		fun px(dp: Float) = (dp * density).toInt()
		val isDocked = dockStyle.isDocked
		background = if (isDocked) dockDrawable else classicBackground
		elevation = if (isDocked) 0f else classicElevation
		(getChildAt(0).layoutParams as FrameLayout.LayoutParams).bottomMargin =
			if (isDocked) px(DOCK_BOTTOM_MARGIN_DP) else 0
		super.setLabelVisibilityMode(if (compact) LABEL_VISIBILITY_UNLABELED else requestedLabelMode)
		itemPaddingTop = if (compact) px(COMPACT_ITEM_PADDING_DP) else defaultItemPaddingTop
		itemPaddingBottom = if (compact) px(COMPACT_ITEM_PADDING_DP) else defaultItemPaddingBottom
		val isTufted = dockStyle == DockStyle.TUFTED
		itemActiveIndicatorHeight = if (compact) px(COMPACT_INDICATOR_HEIGHT_DP) else defaultIndicatorHeight
		// On the cushion the highlights are round, like its buttons, so they stay clear of the tufts between items.
		itemActiveIndicatorWidth = when {
			isTufted -> itemActiveIndicatorHeight
			compact -> px(COMPACT_INDICATOR_WIDTH_DP)
			else -> defaultIndicatorWidth
		}
		itemIconSize = if (compact) px(COMPACT_ICON_DP) else defaultIconSize
		applyMinimumHeight(if (compact) px(COMPACT_HEIGHT_DP) else requestedMinimumHeight)
		// Only the cushion's button needs room above the capsule, and the Classic bar has no gap above it at all.
		val roomAbove = when {
			!isDocked -> null
			isTufted -> if (compact) COMPACT_TUFT_RISE_DP + COMPACT_TUFT_HALO_DP else TUFT_RISE_DP + TUFT_HALO_DP
			else -> 0f
		}
		setPadding(paddingLeft, roomAbove?.let { px(it + DOCK_TOP_SPARE_DP) } ?: 0, paddingRight, paddingBottom)
		dockDrawable.setCushion(isTufted, context.getThemeColor(materialR.attr.colorOutlineVariant), density)
		with(continueButton) {
			iconSize = when (dockStyle) {
				DockStyle.BESIDE -> px(if (compact) COMPACT_BESIDE_ICON_DP else BESIDE_ICON_DP)
				DockStyle.TUFTED -> px(if (compact) COMPACT_TUFT_ICON_DP else TUFT_ICON_DP)
				else -> px(MERGED_ICON_DP)
			}
			foreground = if (isTufted) stitchRing() else null
		}
		mergedShapeRtl = null
		applyButtonColors()
		continueButton.shapeAppearanceModel = roundShape
		// Leaving the split pill gives the items back their own widths.
		getChildAt(0).requestLayout()
		applyDockProgress(continueProgress)
		requestLayout()
	}

	/** A dashed ring of thread just inside the edge, so the cushion's button looks sewn on. */
	private fun stitchRing(): Drawable {
		val ring = GradientDrawable().apply {
			shape = GradientDrawable.OVAL
			setColor(Color.TRANSPARENT)
			val thread = context.getThemeColor(materialR.attr.colorOnPrimaryContainer)
			setStroke(
				(TUFT_STITCH_WIDTH_DP * density).toInt().coerceAtLeast(1),
				ColorUtils.setAlphaComponent(thread, TUFT_STITCH_ALPHA),
				TUFT_STITCH_DASH_DP * density,
				TUFT_STITCH_DASH_DP * density,
			)
		}
		return InsetDrawable(ring, (TUFT_STITCH_INSET_DP * density).toInt())
	}

	/** Beside the dock the button takes the dock's colour; merged it takes the History highlight's, as one pill. */
	private fun applyButtonColors() {
		val (background, content) = when (dockStyle) {
			DockStyle.TUFTED, DockStyle.CLASSIC -> ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorPrimaryContainer)) to
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

	/** Shows or hides the docked Continue button. The Classic bar has none, and leaves Continue to the activity. */
	fun setContinueVisible(visible: Boolean) {
		val isVisible = visible && dockStyle.isDocked
		if (isContinueShown == isVisible) {
			return
		}
		isContinueShown = isVisible
		dockAnimator?.cancel()
		// Enabled belongs to the activity, which turns the button off while a resume loads.
		continueButton.isClickable = isVisible
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
		if (dockStyle == DockStyle.TUFTED) {
			// The button sinks into the cushion as it goes, and the items part around it as it comes.
			continueButton.scaleX = 0.5f + 0.5f * progress
			continueButton.translationY = tuftRise * (1f - progress)
			(getChildAt(0) as? DockMenuView)?.let {
				it.isGapInMiddle = true
				it.centring = progress
				it.extra = TUFT_GAP_DP * density * progress
			}
			requestLayout()
		} else {
			continueButton.scaleX = 0.6f + 0.4f * progress
			continueButton.translationY = 0f
			(getChildAt(0) as? DockMenuView)?.let {
				it.isGapInMiddle = false
				it.extra = if (dockStyle == DockStyle.MERGED) {
					(mergedSegmentWidth + MERGED_GAP_DP * density) * progress
				} else {
					0f
				}
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

	private val tuftSize: Int
		get() = ((if (isCompact) COMPACT_TUFT_SIZE_DP else TUFT_SIZE_DP) * density).toInt()

	private val tuftRise: Float
		get() = (if (isCompact) COMPACT_TUFT_RISE_DP else TUFT_RISE_DP) * density

	/** The radius of the raised seat around the cushion's button. */
	private val tuftSeat: Float
		get() = tuftSize / 2f + (if (isCompact) COMPACT_TUFT_HALO_DP else TUFT_HALO_DP) * density

	/** The capsule moves over by the beside button plus the gap. */
	private val besideRoom: Float
		get() = (besideSize + besideGap) * continueProgress

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		val menu = getChildAt(0)
		when (dockStyle) {
			DockStyle.BESIDE -> layoutBeside(menu)
			DockStyle.MERGED -> layoutMerged(menu)
			DockStyle.TUFTED -> layoutTufted(menu)
			DockStyle.CLASSIC -> continueButton.layout(0, 0, 0, 0)
		}
	}

	/** Tufting buttons sit between the items; the middle one gives way to Continue, which rises out of the cushion. */
	private fun layoutTufted(menu: View) {
		setFlatCapsule(menu)
		val dock = menu as? DockMenuView
		val items = dock?.visibleItems.orEmpty()
		val gapItem = dock?.gapItem(items)
		val extra = dock?.extra ?: 0f
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		val folded = dock?.foldedItem(items)
		val count = (items.size - 1).coerceAtLeast(0)
		val tuftX = FloatArray(count)
		val tuftAlpha = FloatArray(count) { 1f }
		var centerX = menu.left + menu.width / 2f
		for (i in 0 until count) {
			val child = items[i]
			val half = if (child === gapItem) extra / 2f else 0f
			tuftX[i] = menu.left + if (isRtl) child.left - half else child.right + half
			if (child === gapItem) {
				tuftAlpha[i] = 1f - continueProgress
				centerX = tuftX[i]
			} else if (items[i + 1] === folded || child === folded && (i == 0 || items[i - 1] === gapItem)) {
				// Folding History merges the tufts either side of it into one, or none at an end or by the gap.
				tuftAlpha[i] = 1f - continueProgress
			}
		}
		val size = continueButton.measuredWidth
		val buttonLeft = (centerX - size / 2f).toInt()
		val buttonTop = (menu.top - tuftRise).toInt()
		continueButton.layout(buttonLeft, buttonTop, buttonLeft + size, buttonTop + size)
		val seat = if (continueProgress > 0f) tuftSeat * continueButton.scaleX else 0f
		dockDrawable.setCushionDetails(
			tuftX = tuftX,
			tuftAlpha = tuftAlpha,
			tuftY = iconCenterY(items.firstOrNull()) ?: (menu.top + menu.height / 2f),
			haloX = centerX,
			haloY = buttonTop + size / 2f + continueButton.translationY,
			haloRadius = seat,
			scale = size / TUFT_SIZE_DP,
			pucker = continueProgress,
		)
	}

	/** The menu sits this far in from each end of the dock it draws. */
	private val endInset: Int
		get() = ((if (isCompact) COMPACT_DOCK_END_INSET_DP else DOCK_END_INSET_DP) * density).toInt()

	private fun setFlatCapsule(menu: View) {
		dockDrawable.setCapsule(
			left = (menu.left - endInset).toFloat(),
			top = menu.top.toFloat(),
			right = (menu.right + endInset).toFloat(),
			bottom = menu.bottom.toFloat(),
		)
	}

	private fun layoutBeside(menu: View) {
		setFlatCapsule(menu)
		val size = continueButton.measuredWidth
		val gap = besideGap.toInt() + endInset
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
		val centerY = iconCenterY(item) ?: (menu.top + itemPaddingTop + itemActiveIndicatorHeight / 2f)
		val buttonTop = (centerY - continueButton.measuredHeight / 2f).toInt()
		continueButton.layout(
			buttonLeft,
			buttonTop,
			buttonLeft + segment,
			buttonTop + continueButton.measuredHeight,
		)
	}

	/** The vertical centre of an item's icon, in this view's coordinates. */
	private fun iconCenterY(item: View?): Float? {
		val icon = item?.findViewById<View>(materialR.id.navigation_bar_item_icon_view) ?: return null
		tempRect.set(0, 0, icon.width, icon.height)
		offsetDescendantRectToMyCoords(icon, tempRect)
		return tempRect.exactCenterY()
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
		// The empty corners around the capsule belong to the content below. The Classic bar fills its whole width.
		if (ev.actionMasked == MotionEvent.ACTION_DOWN && dockStyle.isDocked &&
			!dockDrawable.isInside(ev.x, ev.y) && !isOnContinueButton(ev)) {
			return false
		}
		return super.dispatchTouchEvent(ev)
	}

	private fun isOnContinueButton(ev: MotionEvent): Boolean = with(continueButton) {
		isVisible && isContinueShown && ev.x >= left && ev.x < right && ev.y >= top && ev.y < bottom
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
			// The Classic bar runs edge to edge.
			val margin = if (dockStyle.isDocked) (DOCK_MARGIN_DP * density).toInt() + endInset else 0
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
				// The compact dock grows by any gap it opens, rather than squeezing its items.
				val gap = (getChildAt(0) as? DockMenuView)?.extra ?: 0f
				width = min(available, (menu.size * COMPACT_ITEM_WIDTH_DP * density + gap).toInt())
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
			DockStyle.CLASSIC -> return
			DockStyle.BESIDE -> besideSize to besideSize
			DockStyle.MERGED -> mergedSegmentWidth to itemActiveIndicatorHeight
			DockStyle.TUFTED -> tuftSize to tuftSize
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

	/**
	 * Brings back a dock that scrolling slid away, for screens that stop scrolling and could not bring it back
	 * otherwise. A dock hidden on purpose, such as during action mode or search, stays hidden.
	 */
	fun revealIfScrolledAway() {
		if (currentState != STATE_UP || translationY == 0f) {
			return
		}
		currentAnimator?.cancel()
		animateTranslation(0F, SLIDE_UP_ANIMATION_DURATION, LinearOutSlowInInterpolator())
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

/**
 * The menu, able to open a gap after one item: after History, so the merged Continue button fits beside its
 * indicator, or in the middle, for the cushion's button.
 */
@SuppressLint("RestrictedApi", "ViewConstructor")
private class DockMenuView(context: Context) : BottomNavigationMenuView(context) {

	/** Extra room after the gap item, in pixels, taken from the other items. */
	var extra = 0f
		set(value) {
			if (field != value) {
				field = value
				requestLayout()
			}
		}

	/** Opens the gap in the middle of the items, taken from all of them, instead of after History. */
	var isGapInMiddle = false
		set(value) {
			if (field != value) {
				field = value
				requestLayout()
			}
		}

	/**
	 * How far a middle gap has settled, from 0 to 1, eased in with the gap. With an odd number of items the halves
	 * cannot match, so History, which Continue stands in for, folds away over it and the rest balance around the gap.
	 */
	var centring = 0f
		set(value) {
			if (field != value) {
				field = value
				requestLayout()
			}
		}

	val visibleItems: List<View>
		get() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

	/** With an odd number of items around a middle gap, the History item that folds away to even them up. */
	fun foldedItem(items: List<View>): View? {
		if (!isGapInMiddle || items.size < 3 || items.size % 2 == 0) {
			return null
		}
		val history = findItemView(R.id.nav_history)
		return items.firstOrNull { it === history }
	}

	/** The item the gap follows, or null when there is none to follow. */
	fun gapItem(items: List<View>): View? {
		if (items.size < 2) {
			return null
		}
		if (isGapInMiddle) {
			val folded = foldedItem(items)
			val rest = if (folded != null) items - folded else items
			// Without a fold, an odd count puts the fewer items first, so with five the gap falls after the second.
			return rest[rest.size / 2 - 1]
		}
		val history = findItemView(R.id.nav_history)
		return items.firstOrNull { it === history }
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		val items = visibleItems
		val gapItem = gapItem(items)
		if (extra <= 0f || gapItem == null) {
			return
		}
		if (isGapInMiddle) {
			val folded = foldedItem(items)
			if (folded != null) {
				measureFolding(items, folded)
			} else {
				measureAroundMiddle(items, items.indexOf(gapItem) + 1)
			}
			return
		}
		val base = items.sumOf { it.measuredWidth } / items.size
		// History keeps its width so its indicator stays whole, and the other items give up the room.
		val narrowed = (base - extra / (items.size - 1)).toInt().coerceAtLeast(0)
		for (child in items) {
			if (child !== gapItem) {
				child.setExactWidth(narrowed)
			}
		}
	}

	/** Shares what the gap leaves between the [before] items ahead of it and the rest, the gap ending up centred. */
	private fun measureAroundMiddle(items: List<View>, before: Int) {
		val total = items.sumOf { it.measuredWidth }.toFloat()
		val left = total - extra
		val even = left / items.size
		val centred = left / 2f / before
		val start = even + (centred - even) * centring
		val end = (left - start * before) / (items.size - before)
		items.forEachIndexed { i, child ->
			child.setExactWidth((if (i < before) start else end).toInt().coerceAtLeast(0))
		}
	}

	/** Narrows [folded] to nothing as the gap settles, the others sharing what it and the gap leave evenly. */
	private fun measureFolding(items: List<View>, folded: View) {
		val total = items.sumOf { it.measuredWidth }.toFloat()
		val foldedWidth = total / items.size * (1f - centring)
		val each = (total - extra - foldedWidth) / (items.size - 1)
		for (child in items) {
			child.setExactWidth((if (child === folded) foldedWidth else each).toInt().coerceAtLeast(0))
		}
	}

	private fun View.setExactWidth(width: Int) = measure(
		MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
		MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
	)

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val items = visibleItems
		val gapItem = gapItem(items)
		val folded = if (extra > 0f) foldedItem(items) else null
		for (child in items) {
			child.alpha = if (child === folded) 1f - centring else 1f
		}
		if (extra <= 0f || gapItem == null) {
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
			used += childWidth + if (child === gapItem) extra else 0f
		}
	}
}
