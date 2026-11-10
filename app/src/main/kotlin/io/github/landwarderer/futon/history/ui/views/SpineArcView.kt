package io.github.landwarderer.futon.history.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlinx.coroutines.Job
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

private const val COVER_ASPECT = 13f / 18f
private const val MIN_SPINE_DP = 40
private const val SPINE_RANGE_DP = 28
private const val SPINE_GAP_DP = 4
private const val SIDE_MARGIN_DP = 16
private const val MAX_COVER_FRACTION = 0.45f
private const val CURVED_RIM_DP = 36
private const val FLAT_RIM_DP = 12
private const val PLANK_DP = 8
private const val LIFT_DP = 12
private const val TOP_ROOM_DP = 16
private const val SELECTED_ELEVATION_DP = 6
private const val MIN_RADIUS_DP = 480
private const val RADIUS_PER_WIDTH = 1.2f
private const val RIM_STROKE_DP = 8
private const val TICK_INNER_DP = 22
private const val TICK_OUTER_DP = 14
private const val TICK_STEP_DEGREES = 5f
private const val TICK_SPAN_DEGREES = 70f
private const val NOTCH_TOP_DP = 14
private const val NOTCH_WIDTH_DP = 16
private const val NOTCH_HEIGHT_DP = 10
private const val FLING_SECONDS = 0.18f
private const val OVERSCROLL = 0.4f
private const val SETTLE_DURATION = 340L
private const val SETTLE_PER_SPINE = 20L
private const val MAX_SETTLE_DURATION = 900L
private const val COLLAPSE_DURATION = 150L
private const val MAX_TICKED_SPINES = 12

/**
 * Spines standing side by side on the rim of a wheel whose hub sits below the view, or on a flat shelf when
 * [isCurved] is off. Dragging sideways turns it, and it settles with one spine selected in the middle.
 */
class SpineArcView(
	context: Context,
	private val binder: SpineBinder,
) : ViewGroup(context) {

	interface Listener {

		fun onSpineSelected(index: Int)

		fun onSpinePulledOut(isPulledOut: Boolean)

		fun onSpineOpen(index: Int)
	}

	var listener: Listener? = null

	var isCurved = true
		set(value) {
			if (field != value) {
				field = value
				// The rim height changes the book height, so every cover needs loading at its new size.
				recycleAll()
				refresh()
			}
		}

	/** Whether the selected spine pulls out to its full cover by itself, as on the wheel. */
	var autoPullOut = true

	var selectedIndex = 0
		private set

	val isSelectedPulledOut: Boolean
		get() = pulledIndex == selectedIndex && pulled > 0.5f

	private val density = resources.displayMetrics.density
	private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
	private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(materialR.attr.colorSurfaceContainerHigh)
	}
	private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp(RIM_STROKE_DP)
		color = context.getThemeColor(materialR.attr.colorSurfaceContainerHighest)
	}
	private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		strokeWidth = dp(2)
		strokeCap = Paint.Cap.ROUND
		color = context.getThemeColor(materialR.attr.colorOutlineVariant)
	}
	private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(appcompatR.attr.colorPrimary)
	}
	private val notchPath = Path()

	private var entries = emptyList<HistoryEntry>()
	private var maxChapters = 1
	/** The fractional index of the spine at the middle of the rim. */
	private var position = 0f
	private var tickIndex = 0
	private var pulledIndex = -1
	private var pulled = 0f
	private var releasingIndex = -1
	private var releasing = 0f
	private var widths = FloatArray(0)
	private var centers = FloatArray(0)
	private val bound = HashMap<Int, SpineView>()
	private val jobs = HashMap<SpineView, Job>()
	private val pool = ArrayList<SpineView>()
	private var settleAnimator: ValueAnimator? = null
	private var pullAnimator: ValueAnimator? = null
	private var velocityTracker: VelocityTracker? = null
	private var downX = 0f
	private var lastX = 0f
	private var isDragging = false

	init {
		setWillNotDraw(false)
	}

	private val rim: Float
		get() = dp(if (isCurved) CURVED_RIM_DP else FLAT_RIM_DP)
	private val availableHeight: Float
		get() = height - paddingTop - paddingBottom - rim - dp(TOP_ROOM_DP)
	/** Tall enough to fill the view, but never so tall that a pulled out cover hides its neighbours. */
	private val bookHeight: Int
		get() = min(availableHeight, width * MAX_COVER_FRACTION / COVER_ASPECT).toInt().coerceAtLeast(1)
	private val coverWidth: Int
		get() = min(bookHeight * COVER_ASPECT, width - dp(SIDE_MARGIN_DP) * 2f).toInt().coerceAtLeast(1)
	/** The spines' bottom edge. On the wheel any spare height is split above the spines and below the rim. */
	private val baseY: Float
		get() {
			val bottom = height - paddingBottom - rim
			return if (isCurved) bottom - (availableHeight - bookHeight).coerceAtLeast(0f) / 2f else bottom
		}
	private val radius: Float
		get() = max(dp(MIN_RADIUS_DP), width * RADIUS_PER_WIDTH)

	fun setEntries(entries: List<HistoryEntry>, selected: Int) {
		settleAnimator?.cancel()
		pullAnimator?.cancel()
		recycleAll()
		this.entries = entries
		maxChapters = entries.maxOfOrNull { it.chaptersCount }?.coerceAtLeast(1) ?: 1
		selectedIndex = selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
		position = selectedIndex.toFloat()
		tickIndex = selectedIndex
		pulledIndex = if (autoPullOut && entries.isNotEmpty()) selectedIndex else -1
		pulled = if (pulledIndex >= 0) 1f else 0f
		releasingIndex = -1
		releasing = 0f
		refresh()
	}

	/** Turns by [delta] spines from the selected one. */
	fun step(delta: Int) = settleTo(selectedIndex + delta)

	fun select(index: Int) = settleTo(index)

	fun setPulledOut(value: Boolean, animate: Boolean = true) {
		if (entries.isEmpty()) {
			return
		}
		settleAnimator?.end()
		if (pulledIndex != selectedIndex) {
			release()
			pulledIndex = selectedIndex
			pulled = 0f
		}
		val target = if (value) 1f else 0f
		if (animate) {
			animatePull(target, SETTLE_DURATION)
		} else {
			pullAnimator?.cancel()
			pulled = target
			releasingIndex = -1
			releasing = 0f
			refresh()
		}
		listener?.onSpinePulledOut(value)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(
			getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
			getDefaultSize(suggestedMinimumHeight, heightMeasureSpec),
		)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		recycleAll()
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = refresh()

	override fun onDetachedFromWindow() {
		settleAnimator?.cancel()
		pullAnimator?.cancel()
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas) {
		if (entries.isEmpty() || width == 0) {
			return
		}
		val cx = width / 2f
		val base = baseY
		if (!isCurved) {
			canvas.drawRect(0f, base, width.toFloat(), base + dp(PLANK_DP), discPaint)
			return
		}
		val r = radius
		val cy = base + r
		canvas.drawCircle(cx, cy, r, discPaint)
		canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth / 2f, rimPaint)
		// The ticks are fixed to the wheel, so they turn with the spines.
		val turn = Math.toDegrees((offsetAt(position) / r).toDouble()).toFloat()
		val first = ceil((turn - TICK_SPAN_DEGREES) / TICK_STEP_DEGREES).toInt()
		val last = floor((turn + TICK_SPAN_DEGREES) / TICK_STEP_DEGREES).toInt()
		for (k in first..last) {
			val angle = Math.toRadians((k * TICK_STEP_DEGREES - turn).toDouble())
			val sin = sin(angle).toFloat()
			val cos = cos(angle).toFloat()
			val inner = r - dp(TICK_INNER_DP)
			val outer = r - dp(TICK_OUTER_DP)
			canvas.drawLine(cx + sin * inner, cy - cos * inner, cx + sin * outer, cy - cos * outer, tickPaint)
		}
		val notchTop = base + dp(NOTCH_TOP_DP)
		notchPath.rewind()
		notchPath.moveTo(cx, notchTop)
		notchPath.lineTo(cx + dp(NOTCH_WIDTH_DP) / 2f, notchTop + dp(NOTCH_HEIGHT_DP))
		notchPath.lineTo(cx - dp(NOTCH_WIDTH_DP) / 2f, notchTop + dp(NOTCH_HEIGHT_DP))
		notchPath.close()
		canvas.drawPath(notchPath, notchPaint)
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		when (ev.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				downX = ev.x
				lastX = ev.x
				isDragging = false
				velocityTracker?.clear()
				track(ev)
			}

			MotionEvent.ACTION_MOVE -> {
				track(ev)
				if (!isDragging && abs(ev.x - downX) > touchSlop) {
					startDrag(ev.x)
				}
			}
		}
		return isDragging
	}

	@Suppress("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (entries.isEmpty()) {
			return false
		}
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				downX = event.x
				lastX = event.x
				isDragging = false
				velocityTracker?.clear()
				track(event)
			}

			MotionEvent.ACTION_MOVE -> {
				track(event)
				if (!isDragging && abs(event.x - downX) > touchSlop) {
					startDrag(event.x)
				}
				if (isDragging) {
					dragBy(event.x - lastX)
					lastX = event.x
				}
			}

			MotionEvent.ACTION_UP -> {
				track(event)
				if (isDragging) {
					val tracker = velocityTracker
					tracker?.computeCurrentVelocity(1000)
					fling(tracker?.xVelocity ?: 0f)
				}
				endTouch()
			}

			MotionEvent.ACTION_CANCEL -> {
				if (isDragging) {
					fling(0f)
				}
				endTouch()
			}
		}
		return true
	}

	private fun track(event: MotionEvent) {
		(velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }).addMovement(event)
	}

	private fun endTouch() {
		isDragging = false
		velocityTracker?.recycle()
		velocityTracker = null
	}

	private fun startDrag(x: Float) {
		isDragging = true
		lastX = x
		parent?.requestDisallowInterceptTouchEvent(true)
		settleAnimator?.cancel()
		if (pulledIndex >= 0 && pulled > 0f) {
			release()
			animatePull(0f, COLLAPSE_DURATION)
			listener?.onSpinePulledOut(false)
		}
	}

	private fun dragBy(dx: Float) {
		val count = entries.size
		val spacing = spacingAt(position)
		position = (position - dx / spacing).coerceIn(-OVERSCROLL, count - 1 + OVERSCROLL)
		val live = position.roundToInt().coerceIn(0, count - 1)
		tickIndex = live
		if (live != selectedIndex) {
			selectedIndex = live
			tick()
			listener?.onSpineSelected(live)
		}
		refresh()
	}

	private fun fling(velocity: Float) {
		val target = (position - velocity * FLING_SECONDS / spacingAt(position)).roundToInt()
		settleTo(target)
	}

	private fun settleTo(target: Int) {
		val count = entries.size
		if (count == 0) {
			return
		}
		settleAnimator?.cancel()
		pullAnimator?.cancel()
		val to = target.coerceIn(0, count - 1)
		if (to != selectedIndex) {
			selectedIndex = to
			listener?.onSpineSelected(to)
		}
		if (pulledIndex != to) {
			release()
			pulledIndex = to
			pulled = 0f
		}
		val from = position
		val pullFrom = pulled
		val pullTo = if (autoPullOut) 1f else 0f
		val releaseFrom = releasing
		val distance = abs(to - from)
		val ticks = distance <= MAX_TICKED_SPINES
		tickIndex = from.roundToInt()
		settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = (SETTLE_DURATION + (SETTLE_PER_SPINE * distance).toLong()).coerceAtMost(MAX_SETTLE_DURATION)
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener {
				val t = it.animatedFraction
				position = from + (to - from) * t
				pulled = pullFrom + (pullTo - pullFrom) * t
				releasing = releaseFrom * (1f - t)
				val near = position.roundToInt()
				if (near != tickIndex) {
					tickIndex = near
					if (ticks) {
						tick()
					}
				}
				refresh()
			}
			start()
		}
		if ((pullFrom > 0.5f) != (pullTo > 0.5f)) {
			listener?.onSpinePulledOut(pullTo > 0.5f)
		}
	}

	/** Lets the pulled out spine go back in while another one takes its place. */
	private fun release() {
		if (pulledIndex >= 0 && pulled > 0f) {
			releasingIndex = pulledIndex
			releasing = pulled
		}
		pulledIndex = -1
		pulled = 0f
	}

	private fun animatePull(target: Float, duration: Long) {
		pullAnimator?.cancel()
		val pullFrom = pulled
		val releaseFrom = releasing
		pullAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
			this.duration = duration
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener {
				val t = it.animatedFraction
				pulled = pullFrom + (target - pullFrom) * t
				releasing = releaseFrom * (1f - t)
				refresh()
			}
			start()
		}
	}

	private fun tick() {
		performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
	}

	private fun pullOf(index: Int): Float = when (index) {
		pulledIndex -> pulled
		releasingIndex -> releasing
		else -> 0f
	}

	private fun spineWidth(index: Int): Float =
		dp(MIN_SPINE_DP) + dp(SPINE_RANGE_DP) * entries[index].chaptersCount / maxChapters

	private fun computeWidths() {
		val count = entries.size
		if (widths.size != count) {
			widths = FloatArray(count)
			centers = FloatArray(count)
		}
		val cover = coverWidth.toFloat()
		val gap = dp(SPINE_GAP_DP)
		var x = 0f
		for (i in 0 until count) {
			val base = spineWidth(i)
			val w = base + (cover - base) * pullOf(i)
			if (i > 0) {
				x += gap
			}
			widths[i] = w
			centers[i] = x + w / 2f
			x += w
		}
	}

	private fun spacingAt(at: Float): Float {
		val count = centers.size
		return when {
			count >= 2 -> {
				val i = floor(at).toInt().coerceIn(0, count - 2)
				centers[i + 1] - centers[i]
			}

			count == 1 -> widths[0] + dp(SPINE_GAP_DP)
			else -> 1f
		}.coerceAtLeast(1f)
	}

	/** Distance along the rim, from the first spine's middle, of the point at fractional index [at]. */
	private fun offsetAt(at: Float): Float {
		val count = centers.size
		if (count == 0) {
			return 0f
		}
		val i = floor(at).toInt().coerceIn(0, count - 1)
		return centers[i] + (at - i) * spacingAt(at)
	}

	private fun refresh() {
		if (width == 0 || height == 0) {
			return
		}
		computeWidths()
		val count = entries.size
		val offset = offsetAt(position)
		val cover = coverWidth.toFloat()
		val r = radius
		val reach = if (isCurved) {
			r * asin(min(1f, (width / 2f + cover) / r))
		} else {
			width / 2f + cover
		}
		bound.keys.filter { it >= count || abs(centers[it] - offset) > reach }.forEach(::recycle)
		val h = bookHeight
		val top = (baseY - h).toInt()
		for (i in 0 until count) {
			val delta = centers[i] - offset
			if (abs(delta) > reach) {
				continue
			}
			val spine = bound[i] ?: obtain(i)
			val w = widths[i]
			val widthPx = w.roundToInt().coerceAtLeast(1)
			if (spine.measuredWidth != widthPx || spine.measuredHeight != h) {
				spine.measure(
					MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
				)
			}
			val left = ((width - widthPx) / 2f).roundToInt()
			spine.layout(left, top, left + widthPx, top + h)
			val isSelected = i == selectedIndex
			if (isCurved) {
				spine.translationX = 0f
				spine.pivotX = widthPx / 2f
				spine.pivotY = h + r
				spine.rotation = Math.toDegrees((delta / r).toDouble()).toFloat()
			} else {
				spine.rotation = 0f
				spine.translationX = delta
			}
			spine.translationY = if (isSelected) -dp(LIFT_DP) else 0f
			spine.translationZ = if (isSelected) dp(SELECTED_ELEVATION_DP) else 0f
			spine.visibleWidth = widthPx.toFloat()
			spine.cropOffset = binder.cropOffset(entries[i].manga, spineWidth(i), cover, widthPx.toFloat())
			spine.isPulledOut = pullOf(i) > 0.5f
		}
		invalidate()
	}

	private fun obtain(index: Int): SpineView {
		val spine = pool.removeLastOrNull() ?: SpineView(context).also {
			it.setOnClickListener(::onSpineClick)
			addViewInLayout(it, -1, generateDefaultLayoutParams(), true)
		}
		spine.visibility = View.VISIBLE
		spine.tag = index
		bound[index] = spine
		val entry = entries[index]
		binder.bind(spine, entry, coverWidth, bookHeight)
		binder.analyze(entry.manga) {
			if (bound[index] === spine) {
				refresh()
			}
		}?.let { jobs[spine] = it }
		return spine
	}

	private fun recycle(index: Int) {
		val spine = bound.remove(index) ?: return
		jobs.remove(spine)?.cancel()
		spine.disposeImage()
		spine.visibility = View.INVISIBLE
		spine.tag = null
		pool += spine
	}

	private fun recycleAll() {
		bound.keys.toList().forEach(::recycle)
	}

	private fun onSpineClick(view: View) {
		if (isDragging) {
			return
		}
		val index = view.tag as? Int ?: return
		when {
			index != selectedIndex -> settleTo(index)
			autoPullOut -> listener?.onSpineOpen(index)
			else -> setPulledOut(!isSelectedPulledOut)
		}
	}

	private fun dp(value: Int) = value * density
}
