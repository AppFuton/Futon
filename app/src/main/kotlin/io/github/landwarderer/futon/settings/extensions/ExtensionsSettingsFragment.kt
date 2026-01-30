package io.github.landwarderer.futon.settings.extensions

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment

@AndroidEntryPoint
class ExtensionsSettingsFragment : BasePreferenceFragment(R.string.extensions),
	SharedPreferences.OnSharedPreferenceChangeListener {

	private val viewModel by viewModels<ExtensionsSettingsViewModel>()

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_extensions)
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
			AppSettings.KEY_TACHIYOMI_EXTENSIONS_ENABLED -> {
				viewModel.onExtensionsEnabledChanged()
			}
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			"browse_extensions" -> {
				(activity as? io.github.landwarderer.futon.settings.SettingsActivity)?.openFragment(
					fragmentClass = ExtensionBrowserFragment::class.java,
					args = null,
					isFromRoot = false,
				)
				true
			}
			else -> super.onPreferenceTreeClick(preference)
		}
	}
}
