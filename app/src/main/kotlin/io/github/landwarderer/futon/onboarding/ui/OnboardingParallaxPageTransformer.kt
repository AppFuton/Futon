package io.github.landwarderer.futon.onboarding.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import io.github.landwarderer.futon.R
import kotlin.math.abs
import kotlin.math.min

class OnboardingParallaxPageTransformer : ViewPager2.PageTransformer {

	override fun transformPage(page: View, position: Float) {
		val width = page.width.toFloat()
		if (width <= 0f) return

		val effectiveWidth = min(width, 1000f)

		when {
			position < -1f -> {
				page.alpha = 0f
			}
			position <= 1f -> {
				val absPos = abs(position)
				page.cameraDistance = 24000f

				// Gentle page-level scale and alpha transition
				val pageScale = 0.94f + (1f - absPos) * 0.06f
				page.scaleX = pageScale
				page.scaleY = pageScale
				page.alpha = 0.4f + (1f - absPos) * 0.6f

				val cardHero = page.findViewById<View>(R.id.cardHero)
				val textTitle = page.findViewById<View>(R.id.textTitle)
				val textSubtitle = page.findViewById<View>(R.id.textSubtitle)
				val cardContent = page.findViewById<View>(R.id.cardContent)

				// Counter-scroll translation: creates deep multi-plane parallax depth
				if (cardHero?.visibility == View.VISIBLE) {
					cardHero.translationX = -position * (effectiveWidth * 0.45f)
					cardHero.cameraDistance = 16000f
					cardHero.rotationY = -position * 20f
					cardHero.rotation = -position * 8f
					val heroScale = 0.88f + (1f - absPos) * 0.12f
					cardHero.scaleX = heroScale
					cardHero.scaleY = heroScale
				}

				textTitle?.translationX = -position * (effectiveWidth * 0.25f)
				textSubtitle?.translationX = -position * (effectiveWidth * 0.16f)
				cardContent?.translationX = -position * (effectiveWidth * 0.08f)

				// Content card subtle scaling
				cardContent?.let { content ->
					val contentScale = 0.94f + (1f - absPos) * 0.06f
					content.scaleX = contentScale
					content.scaleY = contentScale
				}
			}
			else -> {
				page.alpha = 0f
			}
		}
	}
}
