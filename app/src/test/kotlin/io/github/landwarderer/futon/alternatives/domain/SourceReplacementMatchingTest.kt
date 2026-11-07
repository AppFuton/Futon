package io.github.landwarderer.futon.alternatives.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class SourceReplacementMatchingTest {

	@Test
	fun identicalTitlesIgnoringCaseAndSpaces() {
		assertEquals(1f, SourceReplacementMatching.normalizedSimilarity(" One Piece ", "one piece"), 0.0001f)
		assertEquals(1f, SourceReplacementMatching.normalizedSimilarity("", ""), 0.0001f)
	}

	@Test
	fun alternativeTitlesAreConsidered() {
		val seed = manga(1, "Shingeki no Kyojin")
		val candidate = manga(2, "Attack on Titan", altTitles = setOf("Shingeki no Kyojin"))
		assertEquals(1f, SourceReplacementMatching.titleSimilarity(candidate, seed), 0.0001f)
		val unrelated = manga(3, "Completely different")
		assertTrue(SourceReplacementMatching.titleSimilarity(unrelated, seed) < 0.5f)
	}

	@Test
	fun betterMatchPrefersSimilarityThenChapters() {
		assertTrue(SourceReplacementMatching.isBetter(0.9f, 1, 0.8f, 100))
		assertTrue(SourceReplacementMatching.isBetter(0.8f, 101, 0.8f, 100))
		assertFalse(SourceReplacementMatching.isBetter(0.8f, 100, 0.8f, 100))
		assertFalse(SourceReplacementMatching.isBetter(0.7f, 500, 0.8f, 1))
	}

	@Test
	fun sampleKeepsSmallLibrariesWhole() {
		val list = (1L..5L).map { manga(it, "Title $it") }
		assertEquals(list, SourceReplacementMatching.representativeSample(list, 25))
	}

	@Test
	fun sampleSpreadsAcrossTheAlphabet() {
		val list = ('a'..'z').mapIndexed { index, c -> manga(index.toLong(), "$c title") }.shuffled()
		val sample = SourceReplacementMatching.representativeSample(list, 5)
		assertEquals(5, sample.size)
		assertEquals("a title", sample.first().title)
		assertEquals("z title", sample.last().title)
		assertEquals(sample.size, sample.distinctBy { it.id }.size)
	}

	@Test
	fun plansRankByMatchesThenSimilarity() {
		val few = plan(MangaParserSource.entries[0], matched = 2, similarity = 0.99f)
		val manyLoose = plan(MangaParserSource.entries[1], matched = 5, similarity = 0.6f)
		val manyClose = plan(MangaParserSource.entries[2], matched = 5, similarity = 0.9f)
		assertEquals(
			listOf(manyClose, manyLoose, few),
			listOf(few, manyLoose, manyClose).sortedWith(SourceReplacementMatching.planComparator),
		)
	}

	@Test
	fun scopeOptionsAvailability() {
		val options = AlternativeSourceScopeOptions(
			defaultScope = AlternativeSourceScope.Enabled,
			presets = listOf(AlternativeSourceScopeOptions.PresetChoice(7, "Mine")),
			hasDisabledSources = false,
		)
		assertTrue(options.isAvailable(AlternativeSourceScope.Enabled))
		assertFalse(options.isAvailable(AlternativeSourceScope.Disabled))
		assertTrue(options.isAvailable(AlternativeSourceScope.Preset(7)))
		assertFalse(options.isAvailable(AlternativeSourceScope.Preset(8)))
		assertEquals("Mine", options.presetTitle(7))
	}

	@Test
	fun defaultOptionsDetection() {
		val defaults = AlternativesSearchOptions(query = "Title", sourceScope = AlternativeSourceScope.Preset(1))
		assertTrue(defaults.isDefaultFor("Title", AlternativeSourceScope.Preset(1)))
		assertFalse(defaults.isDefaultFor("Title", AlternativeSourceScope.Enabled))
		assertFalse(defaults.copy(hideNoChapters = true).isDefaultFor("Title", AlternativeSourceScope.Preset(1)))
	}

	private fun plan(source: MangaParserSource, matched: Int, similarity: Float) = SourceReplacementPlan(
		source = source,
		matches = (1L..matched.toLong()).associateWith { manga(it, "t$it") },
		total = 25,
		averageTitleSimilarity = similarity,
	)

	private fun manga(id: Long, title: String, altTitles: Set<String> = emptySet()) = Manga(
		id = id,
		title = title,
		altTitles = altTitles,
		url = "/$id",
		publicUrl = "https://example.com/$id",
		rating = 0f,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		source = MangaParserSource.entries.first(),
	)
}
