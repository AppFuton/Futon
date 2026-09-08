package io.github.landwarderer.futon.settings

import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.ChapterCompletionMode
import io.github.landwarderer.futon.reader.initializeReaderTestWorkManager
import io.github.landwarderer.futon.settings.reader.SmartResumeThresholdDialog
import io.github.landwarderer.futon.settings.search.SettingsSearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SmartResumeSettingsTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var searchHelper: SettingsSearchHelper

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        initializeReaderTestWorkManager(instrumentation.targetContext)
        hiltRule.inject()
        PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext).edit()
            .remove(AppSettings.KEY_SMART_RESUME)
            .remove(AppSettings.KEY_SMART_RESUME_MODE)
            .remove(AppSettings.KEY_SMART_RESUME_PERCENTAGE)
            .remove(AppSettings.KEY_SMART_RESUME_PAGES)
            .remove(AppSettings.KEY_SMART_RESUME_WAIT)
            .commit()
    }

    @Test
    fun defaultsDependenciesAndIndependentValuesSurviveRecreation() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val fragment = readerSettings(activity)
                assertFalse(settings.isSmartResumeEnabled)
                assertFalse(settings.isSmartResumeWaitForUpdates)
                assertEquals(90, settings.smartResumePercentage)
                assertEquals(1, settings.smartResumePagesRemaining)
                val mode = fragment.findPreference<ListPreference>(AppSettings.KEY_SMART_RESUME_MODE)!!
                val percentage = fragment.findPreference<EditTextPreference>(AppSettings.KEY_SMART_RESUME_PERCENTAGE)!!
                val pages = fragment.findPreference<EditTextPreference>(AppSettings.KEY_SMART_RESUME_PAGES)!!
                assertFalse(mode.isEnabled)
                assertTrue(percentage.isVisible)
                assertFalse(pages.isVisible)
                fragment.findPreference<SwitchPreferenceCompat>(AppSettings.KEY_SMART_RESUME)!!.isChecked = true
                assertTrue(mode.isEnabled)
                percentage.text = "95"
                mode.value = ChapterCompletionMode.PAGES_REMAINING.name
                assertFalse(percentage.isVisible)
                assertTrue(pages.isVisible)
                pages.text = "2"
                mode.value = ChapterCompletionMode.PERCENTAGE.name
                assertEquals("95", percentage.text)
                assertEquals("2", pages.text)
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(settings.isSmartResumeEnabled)
                assertEquals(95, settings.smartResumePercentage)
                assertEquals(2, settings.smartResumePagesRemaining)
            }
        }
    }

    @Test
    fun numericDialogRejectsInvalidValuesWithoutClosing() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val fragment = readerSettings(activity)
                fragment.findPreference<SwitchPreferenceCompat>(AppSettings.KEY_SMART_RESUME)!!.isChecked = true
                fragment.onDisplayPreferenceDialog(
                    fragment.findPreference<Preference>(AppSettings.KEY_SMART_RESUME_PERCENTAGE)!!,
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = activity.supportFragmentManager
                    .findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") as SmartResumeThresholdDialog
                val alert = dialog.requireDialog() as AlertDialog
                val editor = alert.findViewById<EditText>(android.R.id.edit)!!
                for (invalid in listOf("", "0", "101", "-1", "1.5", "2147483648")) {
                    editor.setText(invalid)
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    assertTrue(alert.isShowing)
                    assertNotNull(editor.error)
                    assertEquals(90, settings.smartResumePercentage)
                }
                editor.setText("91")
                alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertEquals(91, settings.smartResumePercentage)
            }
        }
    }

    @Test
    fun helpButtonIsAccessibleAndDoesNotToggleWaiting() {
        launch().use { scenario ->
            scenario.onActivity { readerSettings(it).scrollToPreference(AppSettings.KEY_SMART_RESUME_WAIT) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val help = activity.findViewById<View>(R.id.preference_help)
                assertNotNull(help)
                assertTrue(help.isEnabled)
                assertFalse(help.contentDescription.isNullOrBlank())
                help.performClick()
                assertFalse(settings.isSmartResumeWaitForUpdates)
            }
        }
    }

    @Test
    fun thresholdSearchTargetsVisibleParentInBothModes() {
        for (mode in ChapterCompletionMode.entries) {
            PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext).edit()
                .putString(AppSettings.KEY_SMART_RESUME_MODE, mode.name).commit()
            val results = searchHelper.inflatePreferences().filter {
                it.title == instrumentation.targetContext.getString(R.string.smart_resume_threshold)
            }
            assertEquals(1, results.size)
            assertEquals(AppSettings.KEY_SMART_RESUME, results.single().key)
        }
    }

    private fun launch(): ActivityScenario<SettingsActivity> = ActivityScenario.launch(
        Intent(instrumentation.targetContext, SettingsActivity::class.java).setAction(AppRouter.ACTION_READER),
    )

    private fun readerSettings(activity: SettingsActivity) =
        activity.supportFragmentManager.findFragmentById(R.id.container) as ReaderSettingsFragment
}
