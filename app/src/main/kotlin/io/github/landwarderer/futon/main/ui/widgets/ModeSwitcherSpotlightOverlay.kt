package io.github.landwarderer.futon.main.ui.widgets

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.core.view.doOnLayout
import io.github.landwarderer.futon.databinding.LayoutModeSwitcherSpotlightBinding
import kotlin.math.max
import kotlin.math.min

class ModeSwitcherSpotlightOverlay(
	context: Context,
	private val targetView: View,
	private val onDismiss: (demoToggle: Boolean) -> Unit,
) : FrameLayout(context) {

	private val binding = LayoutModeSwitcherSpotlightBinding.inflate(LayoutInflater.from(context), this, true)
	private var haloAnimator: ValueAnimator? = null
	private var backCallback: OnBackPressedCallback? = null

	init {
		alpha = 0f
		setOnClickListener {
			dismiss(demoToggle = true)
		}
		binding.buttonGotIt.setOnClickListener {
			dismiss(demoToggle = true)
		}
		binding.cardCallout.setOnClickListener {
			dismiss(demoToggle = true)
		}
	}

	fun showIn(parent: ViewGroup) {
		parent.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		animate().alpha(1f).setDuration(250).start()

		(context as? OnBackPressedDispatcherOwner)?.let { owner ->
			val callback = object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() {
					dismiss(demoToggle = false)
				}
			}
			owner.onBackPressedDispatcher.addCallback(owner, callback)
			backCallback = callback
		}

		doOnLayout {
			positionElements()
			startHaloPulseAnimation()
			animateCalloutEntrance()
		}
	}

	private fun positionElements() {
		val targetLoc = IntArray(2)
		targetView.getLocationInWindow(targetLoc)
		val overlayLoc = IntArray(2)
		getLocationInWindow(overlayLoc)

		val targetCenterX = (targetLoc[0] - overlayLoc[0] + targetView.width / 2f)
		val targetCenterY = (targetLoc[1] - overlayLoc[1] + targetView.height / 2f)

		val ringWidth = binding.viewSpotlightRing.width.toFloat()
		val ringHeight = binding.viewSpotlightRing.height.toFloat()
		binding.viewSpotlightRing.x = targetCenterX - ringWidth / 2f
		binding.viewSpotlightRing.y = targetCenterY - ringHeight / 2f

		val haloWidth = binding.viewHaloRing.width.toFloat()
		val haloHeight = binding.viewHaloRing.height.toFloat()
		binding.viewHaloRing.x = targetCenterX - haloWidth / 2f
		binding.viewHaloRing.y = targetCenterY - haloHeight / 2f

		binding.cardCallout.doOnLayout { card ->
			val cardWidth = card.width.toFloat()
			val margin = 16f * resources.displayMetrics.density

			var cardX = targetCenterX - cardWidth + (ringWidth / 2f)
			cardX = max(margin, min(cardX, width - cardWidth - margin))

			val cardY = targetCenterY + (ringHeight / 2f) + (12f * resources.displayMetrics.density)

			card.x = cardX
			card.y = cardY
		}
	}

	private fun startHaloPulseAnimation() {
		haloAnimator?.cancel()
		val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.45f)
		val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.45f)
		val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0.0f)

		haloAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.viewHaloRing, scaleX, scaleY, alpha).apply {
			duration = 1300
			repeatCount = ValueAnimator.INFINITE
			repeatMode = ValueAnimator.RESTART
			interpolator = AccelerateDecelerateInterpolator()
			start()
		}

		val ringScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.08f)
		val ringScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.08f)
		ObjectAnimator.ofPropertyValuesHolder(binding.viewSpotlightRing, ringScaleX, ringScaleY).apply {
			duration = 900
			repeatCount = ValueAnimator.INFINITE
			repeatMode = ValueAnimator.REVERSE
			interpolator = AccelerateDecelerateInterpolator()
			start()
		}
	}

	private fun animateCalloutEntrance() {
		binding.cardCallout.scaleX = 0.7f
		binding.cardCallout.scaleY = 0.7f
		binding.cardCallout.alpha = 0f
		binding.cardCallout.animate()
			.scaleX(1.0f)
			.scaleY(1.0f)
			.alpha(1.0f)
			.setDuration(400)
			.setInterpolator(OvershootInterpolator(1.3f))
			.start()
	}

	fun dismiss(demoToggle: Boolean) {
		performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
		haloAnimator?.cancel()
		backCallback?.remove()
		backCallback = null
		animate()
			.alpha(0f)
			.setDuration(200)
			.withEndAction {
				(parent as? ViewGroup)?.removeView(this)
				onDismiss(demoToggle)
			}
			.start()
	}
}
