package io.github.landwarderer.futon.onboarding

import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.onboarding.model.OnboardingPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class OnboardingPageTest {

	@Test
	fun testOnboardingPageCountAndOrdering() {
		val pages = OnboardingPage.entries
		assertEquals(4, pages.size)
		assertEquals(OnboardingPage.WELCOME, pages[0])
		assertEquals(OnboardingPage.DUAL_MODES, pages[1])
		assertEquals(OnboardingPage.SOURCES_AND_NSFW, pages[2])
		assertEquals(OnboardingPage.GET_STARTED, pages[3])
	}

	@Test
	fun testWarningPageFlag() {
		assertFalse(OnboardingPage.WELCOME.isWarningPage)
		assertFalse(OnboardingPage.DUAL_MODES.isWarningPage)
		assertTrue(OnboardingPage.SOURCES_AND_NSFW.isWarningPage)
		assertFalse(OnboardingPage.GET_STARTED.isWarningPage)
	}

	@Test
	fun testPageResourcesArePopulated() {
		for (page in OnboardingPage.entries) {
			if (page == OnboardingPage.WELCOME) {
				assertEquals(R.drawable.ic_futon, page.heroIconRes)
			} else {
				assertNull(page.heroIconRes)
			}
			assertTrue(page.badgeRes != 0)
			assertTrue(page.titleRes != 0)
			assertTrue(page.subtitleRes != 0)
			assertTrue(page.accentColorAttr != 0)

			if (!page.isWarningPage) {
				assertNotNull(page.feature1Res)
				assertNotNull(page.feature2Res)
				assertNotNull(page.feature3Res)
			}
		}
	}

	@Test
	fun testAccentColorsConfigured() {
		assertEquals(appcompatR.attr.colorPrimary, OnboardingPage.WELCOME.accentColorAttr)
		assertEquals(materialR.attr.colorTertiary, OnboardingPage.DUAL_MODES.accentColorAttr)
		assertEquals(appcompatR.attr.colorError, OnboardingPage.SOURCES_AND_NSFW.accentColorAttr)
		assertEquals(appcompatR.attr.colorPrimary, OnboardingPage.GET_STARTED.accentColorAttr)
	}

	@Test
	fun testAppSettingsOnboardingConstants() {
		assertEquals(1, AppSettings.CURRENT_ONBOARDING_VERSION)
		assertEquals("onboarding_version", AppSettings.KEY_ONBOARDING_VERSION)
		assertEquals("mode_switcher_spotlight_shown", AppSettings.KEY_MODE_SWITCHER_SPOTLIGHT_SHOWN)
		assertEquals("about_onboarding", AppSettings.KEY_ABOUT_ONBOARDING)
	}
}
