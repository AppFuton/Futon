package io.github.landwarderer.futon.core.ui.widgets

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import kotlin.math.hypot
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

	private var isCushion = false
	private var unit = 1f
	private val stitchPath = Path()
	private val stitchRect = RectF()
	private val puckerPath = Path()
	private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
	private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
	private val tuftPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val puckerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	private var tuftX = FloatArray(0)
	private var tuftAlpha = FloatArray(0)
	private var tuftY = 0f
	private var haloX = 0f
	private var haloY = 0f
	private var haloRadius = 0f
	private var puckerAlpha = 0f

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
		// When space runs short, shorten the slopes before the flat top, so the top still clears the button.
		shoulder = min(wanted, available).coerceAtLeast(0f)
		topHalfWidth = min(top, shoulder)
		rebuildPath()
		invalidateSelf()
	}

	/** Draws a stitched cushion with tufting buttons instead of the plain capsule, its thread in [thread]. */
	fun setCushion(enabled: Boolean, @ColorInt thread: Int, density: Float) {
		isCushion = enabled
		unit = density
		threadPaint.color = thread
		threadPaint.strokeWidth = STITCH_WIDTH_DP * density
		threadPaint.pathEffect = DashPathEffect(floatArrayOf(STITCH_DASH_DP * density, STITCH_DASH_DP * density), 0f)
		ringPaint.color = thread
		ringPaint.strokeWidth = TUFT_RING_WIDTH_DP * density
		tuftPaint.color = thread
		puckerPaint.color = thread
		puckerPaint.strokeWidth = PUCKER_WIDTH_DP * density
		rebuildPath()
		invalidateSelf()
	}

	/**
	 * Places the tufting buttons at [tuftX] along [tuftY], each faded by [tuftAlpha], and a raised round seat of
	 * [haloRadius] under the Continue button at [haloX], [haloY], with the cushion puckered around it by [pucker].
	 * [scale] is the size of one design dp, in pixels.
	 */
	fun setCushionDetails(
		tuftX: FloatArray,
		tuftAlpha: FloatArray,
		tuftY: Float,
		haloX: Float,
		haloY: Float,
		haloRadius: Float,
		scale: Float,
		@FloatRange(from = 0.0, to = 1.0) pucker: Float,
	) {
		this.tuftX = tuftX
		this.tuftAlpha = tuftAlpha
		this.tuftY = tuftY
		this.haloX = haloX
		this.haloY = haloY
		this.haloRadius = haloRadius
		puckerAlpha = pucker
		puckerPath.reset()
		if (pucker > 0f) {
			for (side in floatArrayOf(-1f, 1f)) {
				puckerPath.moveTo(haloX + side * 21f * scale, haloY + 33f * scale)
				puckerPath.quadTo(haloX + side * 15f * scale, haloY + 30f * scale, haloX + side * 12f * scale, haloY + 25f * scale)
				puckerPath.moveTo(haloX + side * 7f * scale, haloY + 39f * scale)
				puckerPath.quadTo(haloX + side * 5f * scale, haloY + 35f * scale, haloX + side * 5f * scale, haloY + 30f * scale)
			}
		}
		rebuildPath()
		invalidateSelf()
	}

	fun isInside(x: Float, y: Float): Boolean {
		if (capsule.contains(x, y)) {
			return true
		}
		if (isCushion) {
			return haloRadius > 0f && hypot(x - haloX, y - haloY) <= haloRadius
		}
		val bumpTop = capsule.top - bumpHeight * bumpProgress
		return bumpProgress > 0f && y in bumpTop..capsule.top && x in capsule.centerX() - shoulder..capsule.centerX() + shoulder
	}

	override fun draw(canvas: Canvas) {
		canvas.drawPath(path, paint)
		if (!isCushion) {
			return
		}
		canvas.drawPath(stitchPath, threadPaint)
		val base = paint.alpha
		for (i in tuftX.indices) {
			val alpha = (tuftAlpha.getOrElse(i) { 1f } * base).toInt()
			if (alpha <= 0) {
				continue
			}
			tuftPaint.alpha = alpha
			ringPaint.alpha = alpha
			canvas.drawCircle(tuftX[i], tuftY, TUFT_RADIUS_DP * unit, tuftPaint)
			canvas.drawCircle(tuftX[i], tuftY, TUFT_RING_DP * unit, ringPaint)
		}
		if (haloRadius > 0f) {
			// The seat covers the stitching it sits on.
			canvas.drawCircle(haloX, haloY, haloRadius, paint)
			puckerPaint.alpha = (puckerAlpha * PUCKER_ALPHA * base).toInt()
			canvas.drawPath(puckerPath, puckerPaint)
		}
	}

	override fun setAlpha(alpha: Int) {
		paint.alpha = alpha
		threadPaint.alpha = alpha
		invalidateSelf()
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		for (p in arrayOf(paint, threadPaint, ringPaint, tuftPaint, puckerPaint)) {
			p.colorFilter = colorFilter
		}
		invalidateSelf()
	}

	@Deprecated("Deprecated in Java")
	override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

	private fun rebuildPath() {
		path.reset()
		stitchPath.reset()
		if (capsule.isEmpty) {
			return
		}
		if (isCushion) {
			addCushion(path, capsule)
			val inset = STITCH_INSET_DP * unit
			stitchRect.set(capsule.left + inset, capsule.top + inset, capsule.right - inset, capsule.bottom - inset)
			if (!stitchRect.isEmpty) {
				addCushion(stitchPath, stitchRect)
			}
			if (haloRadius > 0f) {
				path.addCircle(haloX, haloY, haloRadius, Path.Direction.CW)
			}
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

	/** A capsule whose long sides dip inwards a little, like a stuffed cushion pulled in at the seams. */
	private fun addCushion(target: Path, rect: RectF) {
		val radius = rect.height() / 2f
		val sag = rect.height() * CUSHION_SAG * 2f
		target.moveTo(rect.left + radius, rect.top)
		target.quadTo(rect.centerX(), rect.top + sag, rect.right - radius, rect.top)
		target.arcTo(rect.right - radius * 2f, rect.top, rect.right, rect.bottom, -90f, 180f, false)
		target.quadTo(rect.centerX(), rect.bottom - sag, rect.left + radius, rect.bottom)
		target.arcTo(rect.left, rect.top, rect.left + radius * 2f, rect.bottom, 90f, 180f, false)
		target.close()
	}

	private companion object {

		/** How far the cushion's long sides dip in, as a share of its height. */
		const val CUSHION_SAG = 0.04f
		const val STITCH_INSET_DP = 6f
		const val STITCH_WIDTH_DP = 1.2f
		const val STITCH_DASH_DP = 4f
		const val TUFT_RADIUS_DP = 3f
		const val TUFT_RING_DP = 6.5f
		const val TUFT_RING_WIDTH_DP = 1f
		const val PUCKER_WIDTH_DP = 1.2f
		const val PUCKER_ALPHA = 0.6f

		/** Horizontal run of each slope, as a multiple of the bump height. Larger is gentler. */
		const val SLOPE_SPAN = 2.1f

		/** How far in from the button's outer radius the flat top ends. */
		const val TOP_INSET = 1.1f

		/** Control point placement that makes a cubic curve follow half a sine wave. */
		const val SLOPE_CURVE = 0.37f
	}
}
