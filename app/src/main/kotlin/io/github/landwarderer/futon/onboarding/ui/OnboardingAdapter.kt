package io.github.landwarderer.futon.onboarding.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.core.util.ext.resolveDp
import io.github.landwarderer.futon.databinding.ItemOnboardingPageBinding
import io.github.landwarderer.futon.onboarding.model.OnboardingPage

class OnboardingAdapter(
	private val pages: List<OnboardingPage> = OnboardingPage.entries,
	private val onQuickActionClick: () -> Unit,
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = ItemOnboardingPageBinding.inflate(inflater, parent, false)
		return PageViewHolder(binding)
	}

	override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
		holder.bind(pages[position])
	}

	override fun onViewAttachedToWindow(holder: PageViewHolder) {
		super.onViewAttachedToWindow(holder)
		if (holder.bindingAdapterPosition == 0) {
			holder.animateEntrance()
		} else {
			holder.startGlowAnimation()
		}
	}

	override fun onViewDetachedFromWindow(holder: PageViewHolder) {
		super.onViewDetachedFromWindow(holder)
		holder.stopGlowAnimation()
	}

	override fun getItemCount(): Int = pages.size

	inner class PageViewHolder(
		private val binding: ItemOnboardingPageBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		private var glowAnimator: ObjectAnimator? = null

		fun bind(page: OnboardingPage) {
			val accentColor = itemView.context.getThemeColor(page.accentColorAttr)
			val colorState = ColorStateList.valueOf(accentColor)

			val iconRes = page.heroIconRes
			if (iconRes != null) {
				binding.cardHero.isVisible = true
				binding.imageViewHero.setImageResource(iconRes)
				binding.imageViewHero.imageTintList = null
				binding.textViewBadge.updateLayoutParams<ViewGroup.MarginLayoutParams> {
					topMargin = 0
				}
			} else {
				binding.cardHero.isVisible = false
				binding.textViewBadge.updateLayoutParams<ViewGroup.MarginLayoutParams> {
					topMargin = itemView.resources.resolveDp(28)
				}
			}

			binding.textViewBadge.setText(page.badgeRes)
			binding.textViewBadge.setTextColor(accentColor)
			binding.iconFeature1.imageTintList = colorState
			binding.iconFeature2.imageTintList = colorState
			binding.iconFeature3.imageTintList = colorState
			binding.textTitle.setText(page.titleRes)
			binding.textSubtitle.setText(page.subtitleRes)

			if (page.isWarningPage) {
				binding.layoutFeature1.isVisible = false
				binding.layoutFeature2.isVisible = false
				binding.layoutFeature3.isVisible = false
				binding.layoutWarningNotice.isVisible = true
				binding.buttonQuickAction.setOnClickListener {
					onQuickActionClick()
				}
			} else {
				binding.layoutWarningNotice.isVisible = false
				binding.layoutFeature1.isVisible = page.feature1Res != null
				page.feature1Res?.let { binding.textFeature1.setText(it) }

				binding.layoutFeature2.isVisible = page.feature2Res != null
				page.feature2Res?.let { binding.textFeature2.setText(it) }

				binding.layoutFeature3.isVisible = page.feature3Res != null
				page.feature3Res?.let { binding.textFeature3.setText(it) }
			}
		}

		fun animateEntrance() {
			val context = itemView.context
			val density = context.resources.displayMetrics.density
			val dp20 = 20f * density
			val dp28 = 28f * density

			// 1. Hero Card pop and spring bounce (if visible)
			if (binding.cardHero.isVisible) {
				binding.cardHero.scaleX = 0.65f
				binding.cardHero.scaleY = 0.65f
				binding.cardHero.alpha = 0f
				binding.cardHero.animate()
					.scaleX(1.0f)
					.scaleY(1.0f)
					.alpha(1.0f)
					.setDuration(450)
					.setInterpolator(OvershootInterpolator(1.3f))
					.start()
				startGlowAnimation()
			} else {
				stopGlowAnimation()
			}

			// 2. Badge & Title slide-up cascade
			binding.textViewBadge.translationY = dp20
			binding.textViewBadge.alpha = 0f
			binding.textViewBadge.animate()
				.translationY(0f)
				.alpha(1.0f)
				.setStartDelay(if (binding.cardHero.isVisible) 100 else 0)
				.setDuration(350)
				.setInterpolator(DecelerateInterpolator())
				.start()

			binding.textTitle.translationY = dp20
			binding.textTitle.alpha = 0f
			binding.textTitle.animate()
				.translationY(0f)
				.alpha(1.0f)
				.setStartDelay(if (binding.cardHero.isVisible) 140 else 40)
				.setDuration(350)
				.setInterpolator(DecelerateInterpolator())
				.start()

			// 3. Subtitle slide-up
			binding.textSubtitle.translationY = dp20
			binding.textSubtitle.alpha = 0f
			binding.textSubtitle.animate()
				.translationY(0f)
				.alpha(1.0f)
				.setStartDelay(if (binding.cardHero.isVisible) 180 else 80)
				.setDuration(350)
				.setInterpolator(DecelerateInterpolator())
				.start()

			// 4. Content Card / Warning Box slide-up
			binding.cardContent.translationY = dp28
			binding.cardContent.alpha = 0f
			binding.cardContent.animate()
				.translationY(0f)
				.alpha(1.0f)
				.setStartDelay(if (binding.cardHero.isVisible) 240 else 120)
				.setDuration(400)
				.setInterpolator(DecelerateInterpolator())
				.start()
		}

		fun animateSpringSettle() {
			if (binding.cardHero.isVisible) {
				binding.cardHero.animate()
					.scaleX(1.0f)
					.scaleY(1.0f)
					.rotation(0f)
					.rotationY(0f)
					.translationX(0f)
					.setDuration(350)
					.setInterpolator(OvershootInterpolator(1.3f))
					.start()
			}
		}

		fun startGlowAnimation() {
			if (!binding.cardHero.isVisible) return
			if (glowAnimator == null || glowAnimator?.isRunning != true) {
				glowAnimator?.cancel()
				glowAnimator = ObjectAnimator.ofFloat(binding.layoutHeroGlow, "alpha", 0.7f, 1.0f).apply {
					duration = 1800
					repeatMode = ValueAnimator.REVERSE
					repeatCount = ValueAnimator.INFINITE
					interpolator = AccelerateDecelerateInterpolator()
					start()
				}
			}
		}

		fun stopGlowAnimation() {
			glowAnimator?.cancel()
			glowAnimator = null
		}
	}
}
