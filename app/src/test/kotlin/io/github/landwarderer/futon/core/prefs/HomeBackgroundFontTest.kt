package io.github.landwarderer.futon.core.prefs

import android.content.Context
import android.content.SharedPreferences
import io.github.landwarderer.futon.core.ui.util.FontCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class HomeBackgroundFontTest {

	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var context: Context

	@Before
	fun setUp() {
		sharedPreferences = mock(SharedPreferences::class.java)
		context = mock(Context::class.java)
	}

	@Test
	fun testEnumValuesAndDefaults() {
		assertEquals(HomeBackgroundFont.BOLD, HomeBackgroundFont.DEFAULT)
		assertEquals("fonts/MojiwTegami-Standard.ttf", HomeBackgroundFont.LIGHT_THIN.fontAssetPath)
		assertEquals("fonts/ShimbashiGothic-Regular.otf", HomeBackgroundFont.BOLD.fontAssetPath)
		assertEquals("fonts/GenEiKiwamiGo.ttf", HomeBackgroundFont.EXTRA_BOLD.fontAssetPath)
		assertNull(HomeBackgroundFont.NONE.fontAssetPath)
	}

	@Test
	fun testAppSettingsReadDefault() {
		`when`(sharedPreferences.getString(AppSettings.KEY_HOME_BACKGROUND_FONT, null))
			.thenReturn(null)
		val settings = AppSettings(context, sharedPreferences)
		assertEquals(HomeBackgroundFont.BOLD, settings.homeBackgroundFont)
	}

	@Test
	fun testAppSettingsReadCustom() {
		`when`(sharedPreferences.getString(AppSettings.KEY_HOME_BACKGROUND_FONT, null))
			.thenReturn(HomeBackgroundFont.BOLD.name)
		val settings = AppSettings(context, sharedPreferences)
		assertEquals(HomeBackgroundFont.BOLD, settings.homeBackgroundFont)
	}

	@Test
	fun testFontCacheSafelyHandlesMissingAsset() {
		val assetManager = mock(android.content.res.AssetManager::class.java)
		`when`(context.assets).thenReturn(assetManager)
		val result = FontCache.getTypeface(context, "non_existent_font.ttf")
		assertNull("Missing asset should safely return null without throwing", result)
	}
}
