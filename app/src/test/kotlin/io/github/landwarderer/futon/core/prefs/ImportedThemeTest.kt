package io.github.landwarderer.futon.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedThemeTest {

	@Test
	fun testParseValidTheme() {
		val json = """
			{
				"${'$'}schema": "https://example.com/schema.json",
				"version": 1,
				"Catppuccin Mocha": {
					"base": "dark",
					"colours": {
						"mauve": "#cba6f7",
						"base": "#1e1e2e"
					},
					"colour_scheme": {
						"primary": "mauve",
						"on_primary": "base"
					}
				}
			}
		""".trimIndent()

		val themes = ImportedThemeFile.parseImport(json)
		assertEquals(1, themes.size)
		val theme = themes.first()
		assertEquals("catppuccin_mocha", theme.id)
		assertEquals("Catppuccin Mocha", theme.displayName)
		assertTrue(theme.isDark)
		assertEquals(0xFFCBA6F7.toInt(), theme.primaryColor)
		assertEquals(0xFF1E1E2E.toInt(), theme.onPrimaryColor)
	}

	@Test
	fun testParseHexFormatsAndRoleAliases() {
		val json = """
			{
				"custom_palette": {
					"base": "light",
					"display_name": "My Custom Palette",
					"colours": {
						"col1": "#f0a",
						"col2": "0x11223344"
					},
					"colour_scheme": {
						"colorPrimary": "col1",
						"onPrimary": "col2"
					}
				}
			}
		""".trimIndent()

		val themes = ImportedThemeFile.parseImport(json)
		assertEquals(1, themes.size)
		val theme = themes.first()
		assertEquals("custom_palette", theme.id)
		assertEquals("My Custom Palette", theme.displayName)
		assertFalse(theme.isDark)
		assertEquals(0xFFFF00AA.toInt(), theme.primaryColor)
		assertEquals(0x11223344, theme.onPrimaryColor)
	}

	@Test(expected = IllegalArgumentException::class)
	fun testParseRejectsMissingPrimary() {
		val json = """
			{
				"bad_theme": {
					"base": "dark",
					"colours": {
						"c1": "#123456"
					},
					"colour_scheme": {
						"secondary": "c1"
					}
				}
			}
		""".trimIndent()

		ImportedThemeFile.parseImport(json)
	}

	@Test(expected = IllegalArgumentException::class)
	fun testParseRejectsInvalidHex() {
		val json = """
			{
				"bad_hex": {
					"base": "dark",
					"colours": {
						"c1": "not-a-color"
					},
					"colour_scheme": {
						"primary": "c1"
					}
				}
			}
		""".trimIndent()

		ImportedThemeFile.parseImport(json)
	}

	@Test
	fun testMergeStoredAndRoundtrip() {
		val theme1 = ImportedTheme(
			id = "nord",
			displayName = "Nord Light",
			isDark = false,
			primaryColor = 0xFF88C0D0.toInt(),
			onPrimaryColor = 0xFFECEFF4.toInt(),
			json = """{"base":"light","colours":{"blue":"#88c0d0","snow":"#eceff4"},"colour_scheme":{"primary":"blue","on_primary":"snow"}}""",
		)
		val merged = ImportedThemeFile.mergeStored("{}", listOf(theme1))
		val parsed = ImportedThemeFile.parseStored(merged)

		assertEquals(1, parsed.size)
		assertEquals("nord", parsed[0].id)
		assertEquals("Nord Light", parsed[0].displayName)
		assertEquals(0xFF88C0D0.toInt(), parsed[0].primaryColor)
	}
}
