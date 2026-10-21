package io.github.landwarderer.futon.onboarding.ui

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.resolveDp
import io.github.landwarderer.futon.databinding.ActivityOnboardingBinding
import io.github.landwarderer.futon.onboarding.model.OnboardingPage
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {

	@Inject
	lateinit var settings: AppSettings

	private lateinit var adapter: OnboardingAdapter
	private var lastSelectedPosition = 0

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityOnboardingBinding.inflate(layoutInflater))

		adapter = OnboardingAdapter {
			performHapticTap()
			router.openExtensionDownloader()
		}

		viewBinding.viewPager.apply {
			adapter = this@OnboardingActivity.adapter
			offscreenPageLimit = 3
			setPageTransformer(OnboardingParallaxPageTransformer())
			registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
				override fun onPageSelected(position: Int) {
					super.onPageSelected(position)
					if (position != lastSelectedPosition) {
						performHapticTick()
						lastSelectedPosition = position
					}
					updateDots(position)
					updateButtons(position)
					triggerPageEntrance(position)
				}

				override fun onPageScrollStateChanged(state: Int) {
					super.onPageScrollStateChanged(state)
					if (state == ViewPager2.SCROLL_STATE_IDLE) {
						triggerSpringSettle(currentItem)
					}
				}
			})
		}

		setupDotClickListeners()

		viewBinding.buttonSkip.setOnClickListener {
			performHapticTap(it)
			completeOnboarding()
		}

		viewBinding.buttonBack.setOnClickListener {
			performHapticTap(it)
			if (viewBinding.viewPager.currentItem > 0) {
				viewBinding.viewPager.currentItem -= 1
			}
		}

		viewBinding.buttonNext.setOnClickListener {
			val current = viewBinding.viewPager.currentItem
			if (current < adapter.itemCount - 1) {
				performHapticTap(it)
				viewBinding.viewPager.currentItem = current + 1
			} else {
				performHapticConfirm(it)
				completeOnboarding()
			}
		}

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				performHapticTap()
				if (viewBinding.viewPager.currentItem > 0) {
					viewBinding.viewPager.currentItem -= 1
				} else {
					completeOnboarding()
				}
			}
		})

		updateDots(0)
		updateButtons(0)
		triggerPageEntrance(0)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)

		viewBinding.layoutTopBar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.layoutBottomBar.updatePadding(
			bottom = barsInsets.bottom,
			left = barsInsets.left,
			right = barsInsets.right,
		)

		return insets
	}

	private fun setupDotClickListeners() {
		val dots = listOf(
			viewBinding.dot0,
			viewBinding.dot1,
			viewBinding.dot2,
			viewBinding.dot3,
		)
		dots.forEachIndexed { index, dot ->
			dot.setOnClickListener {
				if (viewBinding.viewPager.currentItem != index) {
					performHapticTick(it)
					viewBinding.viewPager.currentItem = index
				}
			}
		}
	}

	private fun updateDots(selectedIndex: Int) {
		val dots = listOf(
			viewBinding.dot0,
			viewBinding.dot1,
			viewBinding.dot2,
			viewBinding.dot3,
		)
		val activeWidth = resources.resolveDp(24)
		val inactiveWidth = resources.resolveDp(8)

		dots.forEachIndexed { index, dot ->
			val isSelected = index == selectedIndex
			val targetWidth = if (isSelected) activeWidth else inactiveWidth
			val currentWidth = dot.layoutParams.width

			if (currentWidth != targetWidth) {
				val anim = ValueAnimator.ofInt(currentWidth, targetWidth).apply {
					duration = 200
					addUpdateListener { animator ->
						val value = animator.animatedValue as Int
						dot.updateLayoutParams { width = value }
					}
				}
				anim.start()
			}

			dot.setBackgroundResource(
				if (isSelected) R.drawable.bg_onboarding_dot_active
				else R.drawable.bg_onboarding_dot_inactive,
			)
			dot.animate()
				.alpha(if (isSelected) 1.0f else 0.45f)
				.setDuration(200)
				.start()
		}
	}

	private fun updateButtons(position: Int) {
		val isFirstPage = position == 0
		val isLastPage = position == OnboardingPage.entries.size - 1

		viewBinding.buttonBack.isInvisible = isFirstPage

		if (isLastPage) {
			viewBinding.buttonNext.setText(R.string.onboarding_get_started)
			viewBinding.buttonNext.setIconResource(R.drawable.ic_check)
		} else {
			viewBinding.buttonNext.setText(R.string.onboarding_next)
			viewBinding.buttonNext.setIconResource(R.drawable.ic_arrow_forward)
		}
	}

	private fun triggerPageEntrance(position: Int) {
		viewBinding.viewPager.post {
			val recyclerView = viewBinding.viewPager.getChildAt(0) as? RecyclerView ?: return@post
			val holder = recyclerView.findViewHolderForAdapterPosition(position) as? OnboardingAdapter.PageViewHolder
			holder?.animateEntrance()
		}
	}

	private fun triggerSpringSettle(position: Int) {
		val recyclerView = viewBinding.viewPager.getChildAt(0) as? RecyclerView ?: return
		val holder = recyclerView.findViewHolderForAdapterPosition(position) as? OnboardingAdapter.PageViewHolder
		holder?.animateSpringSettle()
	}

	private fun performHapticTick(view: View = viewBinding.root) {
		view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
	}

	private fun performHapticTap(view: View = viewBinding.root) {
		view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
	}

	private fun performHapticConfirm(view: View = viewBinding.root) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
		} else {
			view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
		}
	}

	private fun completeOnboarding() {
		settings.onboardingVersion = AppSettings.CURRENT_ONBOARDING_VERSION
		setResult(RESULT_OK)
		finish()
	}
}
