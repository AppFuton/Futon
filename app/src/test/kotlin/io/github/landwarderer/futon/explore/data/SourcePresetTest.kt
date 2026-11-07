package io.github.landwarderer.futon.explore.data

import io.github.landwarderer.futon.core.model.primaryLanguageOf
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePresetTest {

	@Test
	fun emptyValuesRoundTrip() {
		assertEquals(emptySet<String>(), "".splitPresetValues())
		assertEquals(emptySet<String>(), "  ".splitPresetValues())
		assertEquals("", emptySet<String>().joinPresetValues())
	}

	@Test
	fun sourcesRoundTripIncludingMihonAndExternal() {
		val sources = linkedSetOf("MANGADEX", "MIHON_2499283573021220255", "content:com.example/com.example.provider")
		assertEquals(sources, sources.joinPresetValues().splitPresetValues())
	}

	@Test
	fun blankAndDuplicateItemsAreDropped() {
		assertEquals("en,fr", listOf("en", " ", "fr", "en", "").joinPresetValues())
		assertEquals(setOf("en", "fr"), "en,,fr, ,".splitPresetValues())
	}

	@Test
	fun entityMapping() {
		val preset = SourcePresetEntity(
			presetId = 3,
			title = "Weekend",
			languages = "en,pt-BR",
			sources = "MANGADEX,MIHON_1",
			createdAt = 10L,
			sortKey = 2,
			deletedAt = 0L,
		).toSourcePreset()
		assertEquals(3L, preset.id)
		assertEquals(setOf("en", "pt-BR"), preset.languages)
		assertEquals(setOf("MANGADEX", "MIHON_1"), preset.sources)
		assertEquals(2, preset.sortKey)
	}

	@Test
	fun primaryLanguage() {
		assertEquals("pt", primaryLanguageOf("pt-BR"))
		assertEquals("zh", primaryLanguageOf("zh_Hans"))
		assertEquals("en", primaryLanguageOf("EN"))
	}
}
