package io.github.landwarderer.futon.suggestions.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SuggestionRankingTest {

	private val now = Instant.parse("2026-10-01T00:00:00Z")

	@Test
	fun `normalizeTag trims and lowercases`() {
		assertEquals("action", SuggestionRanking.normalizeTag("  ACTION "))
	}

	@Test
	fun `rare recurring tags beat common ones`() {
		val rarity = mapOf("action" to 1.0, "villainess" to 4.0, "romance" to 1.5)
		val seed = listOf("Action", "Action", "Action", "Villainess", "villainess", "Romance", "Romance", "Oddity")
		val result = SuggestionRanking.distinctiveTags(seed, { rarity[it.lowercase()] ?: 10.0 }, limit = 10)
		assertEquals(listOf("Villainess", "Action", "Romance", "Oddity"), result)
	}

	@Test
	fun `single occurrence tags come after recurring ones`() {
		val seed = listOf("Common", "Common", "Rare")
		val result = SuggestionRanking.distinctiveTags(seed, { if (it == "Rare") 100.0 else 1.0 }, limit = 10)
		assertEquals(listOf("Common", "Rare"), result)
	}

	@Test
	fun `distinctiveTags skips blank and respects limit`() {
		val result = SuggestionRanking.distinctiveTags(listOf(" ", "a", "b", "c"), { 1.0 }, limit = 2)
		assertEquals(2, result.size)
		assertFalse(result.any { it.isBlank() })
	}

	@Test
	fun `weightedOverlap weighs rare tags higher`() {
		val rarity = { t: String -> if (t == "villainess") 5.0 else 1.0 }
		val seed = setOf("action", "villainess")
		val rareMatch = SuggestionRanking.weightedOverlap(seed, setOf("villainess", "comedy"), rarity)
		val commonMatch = SuggestionRanking.weightedOverlap(seed, setOf("action", "comedy"), rarity)
		assertTrue(rareMatch > commonMatch)
		assertEquals(1.0, SuggestionRanking.weightedOverlap(seed, seed, rarity), 1e-9)
		assertEquals(0.0, SuggestionRanking.weightedOverlap(seed, emptySet(), rarity), 1e-9)
	}

	@Test
	fun `dropped means barely read and left for weeks`() {
		val old = now - Duration.ofDays(30)
		val recent = now - Duration.ofDays(3)
		assertTrue(SuggestionRanking.isDropped(0.05f, old, now))
		assertFalse(SuggestionRanking.isDropped(0.05f, recent, now))
		assertFalse(SuggestionRanking.isDropped(0.5f, old, now))
		assertFalse(SuggestionRanking.isDropped(-1f, old, now))
	}
}
