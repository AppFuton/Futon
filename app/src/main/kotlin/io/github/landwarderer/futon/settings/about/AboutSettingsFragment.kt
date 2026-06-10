package io.github.landwarderer.futon.settings.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.xml.KonfettiView
import kotlin.random.Random

@AndroidEntryPoint
class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	private val viewModel by viewModels<AboutSettingsViewModel>()
	private var versionClickCount = 0
	private lateinit var konfettiView: KonfettiView

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.app_version, BuildConfig.VERSION_NAME)
		}
		findPreference<Preference>(AppSettings.KEY_LINK_TELEGRAM)?.isVisible = false
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		konfettiView = KonfettiView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			// Ensure it doesn't consume clicks
			isClickable = false
			isFocusable = false
		}

		(view as? ViewGroup)?.addView(konfettiView)
	}

	override fun onDestroyView() {
		(view as? ViewGroup)?.removeView(konfettiView)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				versionClickCount++
				if (versionClickCount == 8) {
					versionClickCount = 0
					triggerEasterEgg()
				}
				true
			}

			AppSettings.KEY_LINK_WEBLATE -> {
				openLink(R.string.url_weblate, preference.title)
				true
			}

			AppSettings.KEY_LINK_GITHUB -> {
				openLink(R.string.url_github, preference.title)
				true
			}

			AppSettings.KEY_LINK_MANUAL -> {
				openLink(R.string.url_user_manual, preference.title)
				true
			}

			AppSettings.KEY_LINK_TELEGRAM -> {
				if (!openLink(R.string.url_telegram, null)) {
					openLink(R.string.url_telegram_web, preference.title)
				}
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun triggerEasterEgg() {
		val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.unicorn)
		val drawableShape = drawable?.let { Shape.DrawableShape(it, true) }

		val presets = listOf(
			Presets.festive(drawableShape),
			Presets.explode(drawableShape),
			Presets.parade(drawableShape),
			Presets.rain(drawableShape)
		)

		val randomPreset = presets[Random.nextInt(presets.size)]
		konfettiView.start(randomPreset)
	}

	private fun openLink(
		@StringRes url: Int,
		title: CharSequence?
	): Boolean = if (router.openExternalBrowser(getString(url), title)) {
		true
	} else {
		Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		false
	}
}
