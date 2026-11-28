package io.github.landwarderer.futon.core.prefs

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anySet
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class NovelAppSettingsTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var context: Context

	@Before
	fun setUp() {
		sharedPreferences = mock(SharedPreferences::class.java)
		context = mock(Context::class.java)
	}

	@Test
	fun testUserSpecifiedNovelDirectoriesDefaultEmpty() {
		`when`(sharedPreferences.getStringSet(AppSettings.KEY_LOCAL_NOVEL_DIRS, emptySet()))
			.thenReturn(emptySet())
		val settings = AppSettings(context, sharedPreferences)
		assertTrue(settings.userSpecifiedNovelDirectories.isEmpty())
	}

	@Test
	fun testUserSpecifiedNovelDirectoriesFiltersUnreadable() {
		val readableDir = tempFolder.newFolder("novel_folder")
		val nonExistentDir = File(tempFolder.root, "non_existent")

		`when`(sharedPreferences.getStringSet(AppSettings.KEY_LOCAL_NOVEL_DIRS, emptySet()))
			.thenReturn(setOf(readableDir.absolutePath, nonExistentDir.absolutePath))

		val settings = AppSettings(context, sharedPreferences)
		val dirs = settings.userSpecifiedNovelDirectories

		assertEquals(1, dirs.size)
		assertTrue(dirs.contains(readableDir))
	}

	@Test
	fun testSetUserSpecifiedNovelDirectories() {
		val editor = mock(SharedPreferences.Editor::class.java)
		`when`(sharedPreferences.edit()).thenReturn(editor)
		`when`(editor.putStringSet(any(), anySet())).thenReturn(editor)

		val settings = AppSettings(context, sharedPreferences)
		val dir1 = File("/storage/novels1")
		val dir2 = File("/storage/novels2")

		settings.userSpecifiedNovelDirectories = setOf(dir1, dir2)

		verify(editor).putStringSet(
			AppSettings.KEY_LOCAL_NOVEL_DIRS,
			setOf(dir1.absolutePath, dir2.absolutePath),
		)
		verify(editor).apply()
	}

	@Test
	fun testCheckAppUpdateResetsAppModeToMangaWhenVersionIncreases() {
		val editor = mock(SharedPreferences.Editor::class.java)
		`when`(sharedPreferences.edit()).thenReturn(editor)
		`when`(editor.putInt(any(), anyInt())).thenReturn(editor)
		`when`(editor.putString(any(), any())).thenReturn(editor)
		`when`(sharedPreferences.getInt(AppSettings.KEY_LAST_APP_VERSION, 0)).thenReturn(100)
		`when`(sharedPreferences.getBoolean(AppSettings.KEY_DISABLE_MODE_SWITCHER, false)).thenReturn(false)
		`when`(sharedPreferences.getString(AppSettings.KEY_APP_MODE, null)).thenReturn(AppMode.NOVEL.name)

		val settings = AppSettings(context, sharedPreferences)
		settings.checkAppUpdate(101)

		verify(editor).putString(AppSettings.KEY_APP_MODE, AppMode.MANGA.name)
		verify(editor).putInt(AppSettings.KEY_LAST_APP_VERSION, 101)
	}

	@Test
	fun testCheckAppUpdatePreservesNovelModeWhenSwitcherDisabled() {
		val editor = mock(SharedPreferences.Editor::class.java)
		`when`(sharedPreferences.edit()).thenReturn(editor)
		`when`(editor.putInt(any(), anyInt())).thenReturn(editor)
		`when`(sharedPreferences.getInt(AppSettings.KEY_LAST_APP_VERSION, 0)).thenReturn(100)
		`when`(sharedPreferences.getBoolean(AppSettings.KEY_DISABLE_MODE_SWITCHER, false)).thenReturn(true)

		val settings = AppSettings(context, sharedPreferences)
		settings.checkAppUpdate(101)

		verify(editor, never()).putString(AppSettings.KEY_APP_MODE, AppMode.MANGA.name)
		verify(editor).putInt(AppSettings.KEY_LAST_APP_VERSION, 101)
	}

	@Test
	fun testCheckAppUpdateDoesNotChangeModeWhenVersionSame() {
		val editor = mock(SharedPreferences.Editor::class.java)
		`when`(sharedPreferences.edit()).thenReturn(editor)
		`when`(sharedPreferences.getInt(AppSettings.KEY_LAST_APP_VERSION, 0)).thenReturn(100)

		val settings = AppSettings(context, sharedPreferences)
		settings.checkAppUpdate(100)

		verify(editor, never()).putString(any(), any())
		verify(editor, never()).putInt(any(), anyInt())
	}

	@Test
	fun testModeSwitcherDisabledPreference() {
		val editor = mock(SharedPreferences.Editor::class.java)
		`when`(sharedPreferences.edit()).thenReturn(editor)
		`when`(editor.putBoolean(any(), anyBoolean())).thenReturn(editor)
		`when`(sharedPreferences.getBoolean(AppSettings.KEY_DISABLE_MODE_SWITCHER, false)).thenReturn(true)

		val settings = AppSettings(context, sharedPreferences)
		assertTrue(settings.isModeSwitcherDisabled)

		settings.isModeSwitcherDisabled = false
		verify(editor).putBoolean(AppSettings.KEY_DISABLE_MODE_SWITCHER, false)
		verify(editor).apply()
	}
}
