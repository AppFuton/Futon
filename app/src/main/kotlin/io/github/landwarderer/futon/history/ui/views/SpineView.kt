package io.github.landwarderer.futon.history.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.graphics.drawable.toDrawable
import coil3.size.Size
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.core.image.CoilImageView
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.core.util.ext.mangaExtra
import org.koitharu.kotatsu.parsers.model.Manga
import kotlin.math.max
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

private const val SCRIM_COLOR = 0xD60D0D0D.toInt()
private const val EDGE_SHADE_COLOR = 0x6B000000

/** A cover drawn at full size but clipped to a strip of it, so it can slide out from spine to full cover. */
class SpineView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = 0,
) : CoilImageView(context, attrs, defStyleAttr) {

	private var coverMatrix: Matrix? = Matrix()
	private val density = resources.displayMetrics.density
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val progressColor = context.getThemeColor(appcompatR.attr.colorPrimary)
	private var edgeShader: Shader? = null
	private var edgeShaderWidth = 0f

	var coverWidth = 0f
		set(value) {
			field = value
			updateCoverMatrix()
		}
	var coverHeight = 0f
		set(value) {
			field = value
			updateCoverMatrix()
		}
	var cropOffset = 0f
		set(value) {
			field = value
			updateCoverMatrix()
			invalidate()
		}
	var visibleWidth = 0f
		set(value) {
			field = value
			invalidate()
		}
	var isPulledOut = false
		set(value) {
			field = value
			invalidate()
		}
	var percent = 0f

	init {
		scaleType = ScaleType.MATRIX
		// A crossfade reports no intrinsic size, which leaves the cover matrix with nothing to scale.
		crossfadeDurationFactor = 0f
		placeholderDrawable = context.getThemeColor(materialR.attr.colorSurfaceContainer).toDrawable()
		errorDrawable = placeholderDrawable
		fallbackDrawable = placeholderDrawable
		background = placeholderDrawable
		shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(6f * density).build()
	}

	fun setCover(manga: Manga, width: Int, height: Int) {
		exactImageSize = Size(width, height)
		enqueueRequest(
			newRequestBuilder()
				.data(manga.coverUrl)
				.mangaExtra(manga)
				.build(),
		)
	}

	override fun setImageDrawable(drawable: Drawable?) {
		super.setImageDrawable(drawable)
		updateCoverMatrix()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val width = visibleWidth
		val height = height.toFloat()
		if (width <= 0f) {
			return
		}
		if (!isPulledOut) {
			paint.shader = edgeShader(width)
			canvas.drawRect(0f, 0f, width, height, paint)
			paint.shader = null
		}
		val barHeight = 5f * density
		paint.color = SCRIM_COLOR
		canvas.drawRect(0f, height - barHeight, width, height, paint)
		paint.color = progressColor
		canvas.drawRect(0f, height - barHeight, width * percent.coerceIn(0f, 1f), height, paint)
	}

	private fun edgeShader(width: Float): Shader {
		edgeShader?.takeIf { edgeShaderWidth == width }?.let { return it }
		return LinearGradient(
			0f, 0f, width, 0f,
			intArrayOf(EDGE_SHADE_COLOR, Color.TRANSPARENT, Color.TRANSPARENT, EDGE_SHADE_COLOR),
			floatArrayOf(0f, 0.22f, 0.78f, 1f),
			Shader.TileMode.CLAMP,
		).also {
			edgeShader = it
			edgeShaderWidth = width
		}
	}

	private fun updateCoverMatrix() {
		val matrix = coverMatrix ?: return
		val image = drawable ?: return
		val imageWidth = image.intrinsicWidth.toFloat()
		val imageHeight = image.intrinsicHeight.toFloat()
		if (imageWidth <= 0f || imageHeight <= 0f || coverWidth <= 0f || coverHeight <= 0f) {
			return
		}
		val scale = max(coverWidth / imageWidth, coverHeight / imageHeight)
		matrix.setScale(scale, scale)
		matrix.postTranslate(
			(coverWidth - imageWidth * scale) / 2f - cropOffset,
			(coverHeight - imageHeight * scale) / 2f,
		)
		imageMatrix = matrix
	}
}
