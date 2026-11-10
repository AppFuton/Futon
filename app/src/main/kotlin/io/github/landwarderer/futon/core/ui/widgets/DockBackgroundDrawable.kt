package io.github.landwarderer.futon.core.ui.widgets

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import kotlin.math.min

/** A floating capsule whose top edge can grow a bump with sloped sides around a pill button. */
class DockBackgroundDrawable(
	@ColorInt color: Int,
) : Drawable() {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
	private val path = Path()
	private val capsule = RectF()
	private var bumpHeight = 0f
	private var topHalfWidth = 0f
	private var shoulder = 0f
	private var lastHalfWidth = 0f
	private var lastRadius = 0f

	@FloatRange(from = 0.0, to = 1.0)
	var bumpProgress = 0f
		set(value) {
			field = value
			rebuildPath()
			invalidateSelf()
		}

	/** The bump surrounds a pill of [halfWidth] and [radius], rising [bump] above the capsule [top]. */
	fun setCapsule(left: Float, top: Float, right: Float, bottom: Float, bump: Float, halfWidth: Float, radius: Float) {
		if (capsule.left == left && capsule.top == top && capsule.right == right && capsule.bottom == bottom &&
			bumpHeight == bump && lastHalfWidth == halfWidth && lastRadius == radius
		) {
			return
		}
		lastHalfWidth = halfWidth
		lastRadius = radius
		capsule.set(left, top, right, bottom)
		bumpHeight = bump
		val available = capsule.centerX() - capsule.left - capsule.height() / 2f
		val top = (halfWidth - radius * TOP_INSET).coerceAtLeast(0f)
		val wanted = top + bump * SLOPE_SPAN
		shoulder = min(wanted, available)
		topHalfWidth = top * (if (wanted > 0f) shoulder / wanted else 1f)
		rebuildPath()
		invalidateSelf()
	}

	fun isInside(x: Float, y: Float): Boolean {
		if (capsule.contains(x, y)) {
			return true
		}
		val bumpTop = capsule.top - bumpHeight * bumpProgress
		return bumpProgress > 0f && y in bumpTop..capsule.top && x in capsule.centerX() - shoulder..capsule.centerX() + shoulder
	}

	override fun draw(canvas: Canvas) {
		canvas.drawPath(path, paint)
	}

	override fun setAlpha(alpha: Int) {
		paint.alpha = alpha
		invalidateSelf()
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		paint.colorFilter = colorFilter
		invalidateSelf()
	}

	@Deprecated("Deprecated in Java")
	override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

	private fun rebuildPath() {
		path.reset()
		if (capsule.isEmpty) {
			return
		}
		val radius = capsule.height() / 2f
		val center = capsule.centerX()
		val top = capsule.top
		val peak = top - bumpHeight * bumpProgress
		val reach = (shoulder - topHalfWidth) * SLOPE_CURVE

		path.moveTo(capsule.left + radius, top)
		path.lineTo(center - shoulder, top)
		path.cubicTo(center - shoulder + reach, top, center - topHalfWidth - reach, peak, center - topHalfWidth, peak)
		path.lineTo(center + topHalfWidth, peak)
		path.cubicTo(center + topHalfWidth + reach, peak, center + shoulder - reach, top, center + shoulder, top)
		path.lineTo(capsule.right - radius, top)
		path.arcTo(capsule.right - radius * 2f, top, capsule.right, capsule.bottom, -90f, 180f, false)
		path.lineTo(capsule.left + radius, capsule.bottom)
		path.arcTo(capsule.left, top, capsule.left + radius * 2f, capsule.bottom, 90f, 180f, false)
		path.close()
	}

	private companion object {

		/** Horizontal run of each slope, as a multiple of the bump height. Larger is gentler. */
		const val SLOPE_SPAN = 1.6f

		/** How far in from the button's outer radius the flat top ends. */
		const val TOP_INSET = 1.1f

		/** Control point placement that makes a cubic curve follow half a sine wave. */
		const val SLOPE_CURVE = 0.37f
	}
}
