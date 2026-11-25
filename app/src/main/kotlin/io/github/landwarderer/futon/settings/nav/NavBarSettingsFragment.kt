package io.github.landwarderer.futon.settings.nav

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment

/** Everything about the bottom navigation bar: its style, a live preview, its tabs and how it behaves. */
@AndroidEntryPoint
class NavBarSettingsFragment :
	BasePreferenceFragment(R.string.navigation_bar),
	SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_nav_bar)
		bindPreviews()
		bindNavSummary()
		bindMinimalTheme()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_DOCK_STYLE -> {
				bindPreviews()
				bindMinimalTheme()
			}

			AppSettings.KEY_NAV_MAIN -> {
				bindPreviews()
				bindNavSummary()
			}

			AppSettings.KEY_MINIMAL_THEME,
			AppSettings.KEY_NAV_LABELS,
			AppSettings.KEY_MAIN_FAB,
			AppSettings.KEY_TRACKER_ENABLED,
			AppSettings.KEY_SUGGESTIONS,
				-> bindPreviews()
		}
	}

	private fun bindPreviews() {
		val look = NavBarLook.from(settings)
		findPreference<NavBarPreviewPreference>(KEY_PREVIEW)?.let {
			it.style = settings.dockStyle
			it.look = look
		}
		findPreference<DockStylePreference>(AppSettings.KEY_DOCK_STYLE)?.look = look
	}

	private fun bindNavSummary() {
		findPreference<Preference>(AppSettings.KEY_NAV_MAIN)?.summary = settings.mainNavItems.joinToString {
			getString(it.title)
		}
	}

	/** The Classic bar has no smaller form. */
	private fun bindMinimalTheme() {
		val pref = findPreference<Preference>(AppSettings.KEY_MINIMAL_THEME) ?: return
		val isAvailable = settings.dockStyle.isDocked
		pref.isEnabled = isAvailable
		pref.setSummary(if (isAvailable) R.string.minimal_theme_summary else R.string.minimal_theme_unavailable)
	}

	private companion object {

		const val KEY_PREVIEW = "nav_bar_preview"
	}
}
