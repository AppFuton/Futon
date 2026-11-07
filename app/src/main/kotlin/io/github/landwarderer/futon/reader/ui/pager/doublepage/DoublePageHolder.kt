package io.github.landwarderer.futon.reader.ui.pager.doublepage

import android.graphics.PointF
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.databinding.ItemPageBinding
import io.github.landwarderer.futon.reader.domain.PageLoader
import io.github.landwarderer.futon.reader.ui.config.ReaderSettings
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import io.github.landwarderer.futon.reader.ui.pager.standard.PageHolder

class DoublePageHolder(
	owner: LifecycleOwner,
	binding: ItemPageBinding,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : PageHolder(
	owner = owner,
	binding = binding,
	loader = loader,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
) {

	private val isEven: Boolean
		get() = bindingAdapterPosition and 1 == 0

	init {
		binding.ssiv.panLimit = SubsamplingScaleImageView.PAN_LIMIT_INSIDE
	}

	private var isSpacer = false

	override fun onBind(data: ReaderPage) {
		super.onBind(data)
		isSpacer = false
		resetTransformations()
		binding.ssiv.isVisible = true
		binding.textViewNumber.isVisible = settings.isPagesNumbersEnabled
		(binding.textViewNumber.layoutParams as FrameLayout.LayoutParams)
			.gravity = (if (isEven) Gravity.START else Gravity.END) or Gravity.BOTTOM
	}

	/**
	 * Display a blank page, used to pad chapters so that two chapters never share a spread
	 */
	fun bindSpacer() {
		isSpacer = true
		viewModel.onRecycle()
		binding.ssiv.recycle()
		resetTransformations()
		binding.ssiv.isVisible = false
		binding.textViewNumber.isVisible = false
		bindingInfo.layoutProgress.isGone = true
		bindingInfo.layoutError.isVisible = false
		settings.applyBackground(itemView)
	}

	override fun onConfigChanged(settings: ReaderSettings) {
		super.onConfigChanged(settings)
		if (isSpacer) {
			binding.textViewNumber.isVisible = false
		}
	}

	override fun onReady() {
		with(binding.ssiv) {
			colorFilter = settings.colorFilter?.toColorFilter()
			minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
			// no per-page zoom: the whole spread is zoomed by DoublePageScalingFrame
			maxScale = minScale
			setScaleAndCenter(
				minScale,
				PointF(sWidth / 2f, sHeight / 2f),
			)
			if (sWidth.toFloat() / sHeight > WIDE_IMAGE_RATIO) {
				// wide image: scale it up to fit the whole spread
				val containerWidth = width.toFloat()
				val parent = itemView.parent as? ViewGroup
				val fullWidth = parent?.width?.toFloat() ?: (containerWidth * 2f)
				val fullHeight = parent?.height?.toFloat() ?: height.toFloat()
				val fitScale = minOf(fullWidth / sWidth, fullHeight / sHeight)
				val ratio = (fitScale / minScale).coerceAtLeast(1f)
				scaleX = ratio
				scaleY = ratio
				translationX = if (isEven) containerWidth / 2f else -containerWidth / 2f
				settings.applyBackground(this)
				itemView.translationZ = 1f
				(itemView as? ViewGroup)?.clipChildren = false
			} else {
				// regular page: push it toward the other page of the spread
				scaleX = 1f
				scaleY = 1f
				background = null
				itemView.translationZ = 0f
				val offset = (width - sWidth * minScale) / 2f
				translationX = when {
					offset <= 0f -> 0f
					isEven -> offset
					else -> -offset
				}
			}
		}
	}

	private fun resetTransformations() {
		with(binding.ssiv) {
			scaleX = 1f
			scaleY = 1f
			translationX = 0f
			background = null
		}
		itemView.translationZ = 0f
	}

	private companion object {

		const val WIDE_IMAGE_RATIO = 1.3f
	}
}
