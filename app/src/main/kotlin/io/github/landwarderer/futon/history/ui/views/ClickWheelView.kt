package io.github.landwarderer.futon.history.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

private const val MAX_RADIUS_DP = 110
private const val BUTTON_RATIO = 0.39f
private const val EDGE_RATIO = 0.68f
private const val DOT_INSET_DP = 10
private const val DOT_RADIUS_DP = 3
private const val EDGE_ICON_DP = 22
private const val CENTER_ICON_DP = 26
private const val LABEL_SP = 11f
private const val LABEL_SPACING = 0.08f
private const val STEP_DEGREES = 24f
private const val TURN_SLOP_DEGREES = 6f
private const val CENTER_RELEASE_SLACK = 1.2f

/** A round control that steps through items as you turn it, with a button in the middle and one at each edge. */
class ClickWheelView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : View(context, attrs) {

	interface Listener {

		fun onWheelStep(delta: Int)

		fun onWheelOpen()

		fun onWheelLatest()

		fun onWheelPeek()
	}

	var listener: Listener? = null

	var isPeeking = false
		set(value) {
			if (field != value) {
				field = value
				invalidate()
			}
		}

	private val density = resources.displayMetrics.density
	private val labelColor = context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
	private val activeColor = context.getThemeColor(appcompatR.attr.colorPrimary)
	private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(materialR.attr.colorSurfaceContainerHigh)
	}
	private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(materialR.attr.colorPrimaryContainer)
	}
	private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activeColor }
	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		textAlign = Paint.Align.CENTER
		textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, resources.displayMetrics)
		typeface = Typeface.DEFAULT_BOLD
		letterSpacing = LABEL_SPACING
	}
	private val prevIcon = icon(R.drawable.ic_prev, labelColor)
	private val nextIcon = icon(R.drawable.ic_next, labelColor)
	private val openIcon = icon(R.drawable.ic_read, context.getThemeColor(materialR.attr.colorOnPrimaryContainer))
	private val latestLabel = context.getString(R.string.history_wheel_latest).uppercase()
	private val peekLabel = context.getString(R.string.history_wheel_peek).uppercase()

	private var cx = 0f
	private var cy = 0f
	private var radius = 0f
	private var buttonRadius = 0f
	/** Where the dot on the ring sits, in degrees clockwise from the right. */
	private var dial = -90f
	private var lastAngle = 0f
	private var turned = 0f
	private var travel = 0f
	private var isTurning = false
	private var isCenterDown = false

	init {
		contentDescription = context.getString(R.string.history_wheel_click)
		addAction(R.string.next) { listener?.onWheelStep(1) }
		addAction(R.string.history_wheel_previous) { listener?.onWheelStep(-1) }
		addAction(R.string.history_wheel_latest) { listener?.onWheelLatest() }
		addAction(R.string.history_wheel_peek) { listener?.onWheelPeek() }
		addAction(R.string.history_wheel_open) { listener?.onWheelOpen() }
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		val contentWidth = w - paddingLeft - paddingRight
		val contentHeight = h - paddingTop - paddingBottom
		cx = paddingLeft + contentWidth / 2f
		cy = paddingTop + contentHeight / 2f
		radius = min(min(contentWidth, contentHeight) / 2f, MAX_RADIUS_DP * density).coerceAtLeast(0f)
		buttonRadius = radius * BUTTON_RATIO
	}

	override fun onDraw(canvas: Canvas) {
		if (radius <= 0f) {
			return
		}
		canvas.drawCircle(cx, cy, radius, ringPaint)
		val dotDistance = radius - DOT_INSET_DP * density
		val dialRadians = Math.toRadians(dial.toDouble())
		canvas.drawCircle(
			cx + cos(dialRadians).toFloat() * dotDistance,
			cy + sin(dialRadians).toFloat() * dotDistance,
			DOT_RADIUS_DP * density,
			dotPaint,
		)
		val edge = radius * EDGE_RATIO
		drawIcon(canvas, prevIcon, cx - edge, cy, EDGE_ICON_DP)
		drawIcon(canvas, nextIcon, cx + edge, cy, EDGE_ICON_DP)
		val baseline = -(textPaint.descent() + textPaint.ascent()) / 2f
		textPaint.color = labelColor
		canvas.drawText(latestLabel, cx, cy - edge + baseline, textPaint)
		textPaint.color = if (isPeeking) activeColor else labelColor
		canvas.drawText(peekLabel, cx, cy + edge + baseline, textPaint)
		canvas.drawCircle(cx, cy, buttonRadius, buttonPaint)
		drawIcon(canvas, openIcon, cx, cy, CENTER_ICON_DP)
	}

	@Suppress("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		val distance = hypot(event.x - cx, event.y - cy)
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				if (distance > radius) {
					return false
				}
				isCenterDown = distance < buttonRadius
				lastAngle = angleOf(event)
				turned = 0f
				travel = 0f
				isTurning = false
				parent?.requestDisallowInterceptTouchEvent(true)
			}

			MotionEvent.ACTION_MOVE -> {
				if (isCenterDown) {
					return true
				}
				var delta = angleOf(event) - lastAngle
				if (delta > 180f) delta -= 360f
				if (delta < -180f) delta += 360f
				lastAngle += delta
				turned += delta
				travel += abs(delta)
				dial += delta
				if (travel > TURN_SLOP_DEGREES) {
					isTurning = true
				}
				while (turned >= STEP_DEGREES) {
					turned -= STEP_DEGREES
					listener?.onWheelStep(1)
				}
				while (turned <= -STEP_DEGREES) {
					turned += STEP_DEGREES
					listener?.onWheelStep(-1)
				}
				invalidate()
			}

			MotionEvent.ACTION_UP -> if (!isTurning) {
				performClick()
				press(event, distance)
			}
		}
		return true
	}

	override fun performClick(): Boolean = super.performClick()

	private fun press(event: MotionEvent, distance: Float) {
		if (isCenterDown) {
			if (distance < buttonRadius * CENTER_RELEASE_SLACK) {
				listener?.onWheelOpen()
			}
			return
		}
		if (distance > radius) {
			return
		}
		val angle = angleOf(event)
		when {
			angle in -135f..-45f -> listener?.onWheelLatest()
			angle in -45f..45f -> listener?.onWheelStep(1)
			angle in 45f..135f -> listener?.onWheelPeek()
			else -> listener?.onWheelStep(-1)
		}
	}

	private fun angleOf(event: MotionEvent) =
		Math.toDegrees(atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())).toFloat()

	private fun drawIcon(canvas: Canvas, icon: Drawable, x: Float, y: Float, sizeDp: Int) {
		val half = (sizeDp * density / 2f).toInt()
		icon.setBounds(x.toInt() - half, y.toInt() - half, x.toInt() + half, y.toInt() + half)
		icon.draw(canvas)
	}

	private fun icon(@DrawableRes resId: Int, @ColorInt color: Int): Drawable =
		checkNotNull(ContextCompat.getDrawable(context, resId)).mutate().apply { setTint(color) }

	private fun addAction(label: Int, action: () -> Unit) {
		ViewCompat.addAccessibilityAction(this, context.getString(label)) { _, _ ->
			action()
			true
		}
	}
}
