package io.github.landwarderer.futon.settings.history

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment
import io.github.landwarderer.futon.settings.utils.NewBadgePreference

/** How the History tab shows recent reading: which view, and that view's own settings. */
@AndroidEntryPoint
class HistorySettingsFragment : BasePreferenceFragment(R.string.history) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		NewBadgePreference.markSeen(requireContext(), "history_views")
		addPreferencesFromResource(R.xml.pref_history)
	}
}
