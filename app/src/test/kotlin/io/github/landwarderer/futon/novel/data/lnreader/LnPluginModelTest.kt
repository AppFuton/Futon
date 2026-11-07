package io.github.landwarderer.futon.novel.data.lnreader.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LnPluginModelTest {

	@Test
	fun testLnPluginSerializationRoundtrip() {
		val original = LnPlugin(
			id = "novelfull",
			name = "NovelFull",
			site = "https://novelfull.com/",
			lang = "English",
			version = "2.3.3",
			url = "https://raw.githubusercontent.com/test/novelfull.js",
			iconUrl = "https://raw.githubusercontent.com/test/icon.png",
			customJsUrl = "https://raw.githubusercontent.com/test/custom.js",
			customCssUrl = "https://raw.githubusercontent.com/test/custom.css",
			isInstalled = true,
			hasUpdate = true,
		)

		val json = original.toJson()
		val restored = LnPlugin.fromJson(json, isInstalled = true, hasUpdate = true)

		assertEquals(original.id, restored.id)
		assertEquals(original.name, restored.name)
		assertEquals(original.site, restored.site)
		assertEquals(original.lang, restored.lang)
		assertEquals(original.version, restored.version)
		assertEquals(original.url, restored.url)
		assertEquals(original.iconUrl, restored.iconUrl)
		assertEquals(original.customJsUrl, restored.customJsUrl)
		assertEquals(original.customCssUrl, restored.customCssUrl)
		assertTrue(restored.isInstalled)
		assertTrue(restored.hasUpdate)
	}

	@Test
	fun testLnPluginFromJsonWithDefaults() {
		val json = JSONObject().apply {
			put("id", "boxnovel")
			put("name", "BoxNovel")
			put("url", "https://raw.githubusercontent.com/test/boxnovel.js")
		}

		val plugin = LnPlugin.fromJson(json)

		assertEquals("boxnovel", plugin.id)
		assertEquals("BoxNovel", plugin.name)
		assertEquals("", plugin.site)
		assertEquals("en", plugin.lang)
		assertEquals("1.0.0", plugin.version)
		assertNull(plugin.customJsUrl)
		assertNull(plugin.customCssUrl)
		assertFalse(plugin.isInstalled)
		assertFalse(plugin.hasUpdate)
	}
}

