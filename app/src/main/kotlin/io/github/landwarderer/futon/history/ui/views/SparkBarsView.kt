package io.github.landwarderer.futon.history.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import kotlin.math.max
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class SparkBarsView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : View(context, attrs) {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private val density = resources.displayMetrics.density
	private val barColor = context.getThemeColor(appcompatR.attr.colorPrimary)
	private val emptyColor = context.getThemeColor(materialR.attr.colorOutlineVariant)
	private var values = LongArray(0)

	fun setValues(data: LongArray) {
		values = data
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (values.isEmpty()) {
			return
		}
		val peak = max(1L, values.max())
		val barWidth = 4f * density
		val step = (width - barWidth) / max(1, values.size - 1)
		val radius = barWidth / 2f
		values.forEachIndexed { i, value ->
			val barHeight = if (value > 0L) max(6f * density, value.toFloat() / peak * height) else 3f * density
			val left = i * step
			rect.set(left, height - barHeight, left + barWidth, height.toFloat())
			paint.color = if (value > 0L) barColor else emptyColor
			canvas.drawRoundRect(rect, radius, radius, paint)
		}
	}
}
