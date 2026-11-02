package io.github.landwarderer.futon.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.names
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.toTitleCase
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.os.AppShortcutManager
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.ColorScheme
import io.github.landwarderer.futon.core.prefs.HomeBackgroundFont
import io.github.landwarderer.futon.core.prefs.ImportedThemeFile
import io.github.landwarderer.futon.core.prefs.ListMode
import io.github.landwarderer.futon.core.prefs.ProgressIndicatorMode
import io.github.landwarderer.futon.core.prefs.ScreenshotsPolicy
import io.github.landwarderer.futon.core.prefs.SearchSuggestionType
import io.github.landwarderer.futon.core.prefs.TriStateOption
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment
import io.github.landwarderer.futon.core.ui.util.ActivityRecreationHandle
import io.github.landwarderer.futon.core.util.LocaleComparator
import io.github.landwarderer.futon.core.util.ext.getLocalesConfig
import io.github.landwarderer.futon.core.util.ext.postDelayed
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.setDefaultValueCompat
import io.github.landwarderer.futon.core.util.ext.sortedWithSafe
import io.github.landwarderer.futon.core.util.ext.toList
import io.github.landwarderer.futon.settings.protect.ProtectSetupActivity
import io.github.landwarderer.futon.settings.utils.ActivityListPreference
import io.github.landwarderer.futon.settings.utils.MultiSummaryProvider
import io.github.landwarderer.futon.settings.utils.PercentSummaryProvider
import io.github.landwarderer.futon.settings.utils.SliderPreference
import io.github.landwarderer.futon.settings.utils.ThemeChooserPreference
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceSettingsFragment :
    BasePreferenceFragment(R.string.appearance),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val customThemeFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCustomTheme(uri)
    }

    @Inject
    lateinit var activityRecreationHandle: ActivityRecreationHandle

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_appearance)
        findPreference<SliderPreference>(AppSettings.KEY_GRID_SIZE)?.summaryProvider = PercentSummaryProvider()
        findPreference<ListPreference>(AppSettings.KEY_LIST_MODE)?.run {
            entryValues = ListMode.entries.names()
            setDefaultValueCompat(ListMode.GRID.name)
        }
        findPreference<ListPreference>(AppSettings.KEY_PROGRESS_INDICATORS)?.run {
            entryValues = ProgressIndicatorMode.entries.names()
            setDefaultValueCompat(ProgressIndicatorMode.PERCENT_READ.name)
        }
        findPreference<ListPreference>(AppSettings.KEY_HOME_BACKGROUND_FONT)?.run {
            entryValues = HomeBackgroundFont.entries.names()
            setDefaultValueCompat(HomeBackgroundFont.DEFAULT.name)
        }
        findPreference<ActivityListPreference>(AppSettings.KEY_APP_LOCALE)?.run {
            initLocalePicker(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityIntent = Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }
            summaryProvider = Preference.SummaryProvider<ActivityListPreference> {
                val locale = AppCompatDelegate.getApplicationLocales().get(0)
                locale?.getDisplayName(locale)?.toTitleCase(locale) ?: getString(R.string.follow_system)
            }
            setDefaultValueCompat("")
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_MANGA_LIST_BADGES)?.run {
            summaryProvider = MultiSummaryProvider(R.string.none)
        }
        findPreference<Preference>(AppSettings.KEY_SHORTCUTS)?.isVisible =
            appShortcutManager.isDynamicShortcutsAvailable()
        findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
            ?.isChecked = !settings.appPassword.isNullOrEmpty()
        findPreference<ListPreference>(AppSettings.KEY_SCREENSHOTS_POLICY)?.run {
            entryValues = ScreenshotsPolicy.entries.names()
            setDefaultValueCompat(ScreenshotsPolicy.ALLOW.name)
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_SEARCH_SUGGESTION_TYPES)?.let { pref ->
            pref.entryValues = SearchSuggestionType.entries.names()
            pref.entries = SearchSuggestionType.entries.map { pref.context.getString(it.titleResId) }.toTypedArray()
            pref.summaryProvider = MultiSummaryProvider(R.string.none)
            pref.values = settings.searchSuggestionTypes.mapToSet { it.name }
        }
        findPreference<Preference>(KEY_CUSTOM_THEMES_MANAGEMENT)?.setOnPreferenceClickListener {
            showCustomThemes()
            true
        }
        findPreference<Preference>(KEY_CUSTOM_THEME_IMPORT)?.setOnPreferenceClickListener {
            customThemeFilePicker.launch(arrayOf("*/*"))
            true
        }
        findPreference<Preference>(KEY_CUSTOM_THEME_DELETE)?.setOnPreferenceClickListener {
            confirmDeleteCustomTheme()
            true
        }
        updateCustomThemeSummaries()
        bindNavSummary()
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
            AppSettings.KEY_THEME -> {
                AppCompatDelegate.setDefaultNightMode(settings.theme)
            }

            AppSettings.KEY_COLOR_THEME,
            AppSettings.KEY_THEME_AMOLED,
                -> {
                postRestart()
            }

            AppSettings.KEY_CUSTOM_THEME -> {
                updateCustomThemeSummaries()
                // Restart so the newly selected custom theme is applied to all activities
                if (settings.colorScheme == ColorScheme.CUSTOM) postRestart()
            }

            AppSettings.KEY_CUSTOM_THEMES -> updateCustomThemeSummaries()

            AppSettings.KEY_APP_LOCALE -> {
                AppCompatDelegate.setApplicationLocales(settings.appLocales)
            }

            AppSettings.KEY_NAV_MAIN -> {
                bindNavSummary()
            }

            AppSettings.KEY_APP_PASSWORD -> {
                findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
                    ?.isChecked = !settings.appPassword.isNullOrEmpty()
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_PROTECT_APP -> {
                val pref = (preference as? TwoStatePreference ?: return false)
                if (pref.isChecked) {
                    pref.isChecked = false
                    startActivity(Intent(preference.context, ProtectSetupActivity::class.java))
                } else {
                    settings.appPassword = null
                }
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun postRestart() {
        viewLifecycleOwner.lifecycle.postDelayed(400) {
            activityRecreationHandle.recreateAll()
        }
    }

    private fun showCustomThemes() {
        val themes = settings.importedThemes
        if (themes.isEmpty()) {
            customThemeFilePicker.launch(arrayOf("*/*"))
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.custom_themes)
            .setSingleChoiceItems(
                themes.map { it.displayName }.toTypedArray(),
                themes.indexOfFirst { it.id == settings.selectedCustomThemeId },
            ) { dialog, index ->
                settings.selectedCustomThemeId = themes[index].id
                findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.value =
                    ColorScheme.CUSTOM.name
                updateCustomThemeSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importCustomTheme(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatchingCancellable {
                // Read from ContentResolver on IO thread to avoid blocking the main thread
                // when importing from cloud storage providers (Drive, Nextcloud, etc.)
                val json = withContext(Dispatchers.IO) { readThemeFile(appContext, uri) }
                val imported = settings.importCustomThemes(json)
                settings.selectedCustomThemeId = imported.last().id
                findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.value =
                    ColorScheme.CUSTOM.name
                findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.refresh()
                updateCustomThemeSummaries()
                context?.let { ctx ->
                    Toast.makeText(ctx, R.string.custom_theme_imported, Toast.LENGTH_SHORT).show()
                }
            }
            result.onFailure {
                it.printStackTraceDebug()
                context?.let { ctx ->
                    Toast.makeText(ctx, R.string.custom_theme_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readThemeFile(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open theme file")
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val length = reader.read(buffer)
                if (length < 0) break
                require(result.length + length <= ImportedThemeFile.MAX_FILE_SIZE) {
                    "Theme file is too large"
                }
                result.append(buffer, 0, length)
            }
            result.toString()
        }
    }

    private fun confirmDeleteCustomTheme() {
        val selected = settings.selectedCustomTheme
        if (selected == null) {
            context?.let { ctx ->
                Toast.makeText(ctx, R.string.custom_themes_none, Toast.LENGTH_SHORT).show()
            }
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.custom_theme_delete)
            .setMessage(getString(R.string.custom_theme_delete_confirm, selected.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                settings.deleteCustomTheme(selected.id)
                if (settings.selectedCustomTheme == null && settings.colorScheme == ColorScheme.CUSTOM) {
                    findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.value =
                        ColorScheme.default.name
                }
                findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.refresh()
                updateCustomThemeSummaries()
            }
            .show()
    }

    private fun updateCustomThemeSummaries() {
        val customTheme = settings.selectedCustomTheme
        val emptySummary = getString(R.string.custom_themes_none)
        findPreference<Preference>(KEY_CUSTOM_THEMES_MANAGEMENT)?.summary =
            customTheme?.displayName ?: emptySummary
        findPreference<Preference>(KEY_CUSTOM_THEME_DELETE)?.apply {
            summary = customTheme?.displayName ?: emptySummary
            isEnabled = customTheme != null
        }
    }

    private fun initLocalePicker(preference: ListPreference) {
        val locales = preference.context.getLocalesConfig()
            .toList()
            .sortedWithSafe(LocaleComparator())
        preference.entries = Array(locales.size + 1) { i ->
            if (i == 0) {
                getString(R.string.follow_system)
            } else {
                val lc = locales[i - 1]
                lc.getDisplayName(lc).toTitleCase(lc)
            }
        }
        preference.entryValues = Array(locales.size + 1) { i ->
            if (i == 0) {
                ""
            } else {
                locales[i - 1].toLanguageTag()
            }
        }
    }

    private fun bindNavSummary() {
        val pref = findPreference<Preference>(AppSettings.KEY_NAV_MAIN) ?: return
        pref.summary = settings.mainNavItems.joinToString {
            getString(it.title)
        }
    }

    private companion object {
        const val KEY_CUSTOM_THEMES_MANAGEMENT = "custom_themes_management"
        const val KEY_CUSTOM_THEME_IMPORT = "custom_theme_import"
        const val KEY_CUSTOM_THEME_DELETE = "custom_theme_delete"
    }
}
