package io.github.landwarderer.futon.settings.nav

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

private const val VIRTUAL_WIDTH_DP = 360f

/**
 * Lays its one child out at a phone's width and draws it shrunk to fit, sitting on the frame's bottom edge, so a
 * full-size bar can be shown in a small card.
 */
class ScaledPreviewFrame @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

	private val virtualWidth = (VIRTUAL_WIDTH_DP * resources.displayMetrics.density).toInt()

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		val child = getChildAt(0)
		if (child == null) {
			setMeasuredDimension(width, 0)
			return
		}
		child.measure(
			MeasureSpec.makeMeasureSpec(virtualWidth, MeasureSpec.EXACTLY),
			MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
		)
		val scale = width.toFloat() / virtualWidth
		val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
			MeasureSpec.getSize(heightMeasureSpec)
		} else {
			(child.measuredHeight * scale).toInt()
		}
		setMeasuredDimension(width, height)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val child = getChildAt(0) ?: return
		child.layout(0, 0, child.measuredWidth, child.measuredHeight)
		val scale = width.toFloat() / virtualWidth
		child.pivotX = 0f
		child.pivotY = 0f
		child.scaleX = scale
		child.scaleY = scale
		child.translationY = height - child.measuredHeight * scale
	}
}
