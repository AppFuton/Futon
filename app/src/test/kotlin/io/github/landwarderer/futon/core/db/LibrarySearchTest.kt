package io.github.landwarderer.futon.core.db

import io.github.landwarderer.futon.core.db.SourceSearchFilter.SourceCandidate
import io.github.landwarderer.futon.list.domain.ListFilterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

	private val candidates = listOf(
		SourceCandidate("MANGAFIRE_EN", "MangaFire (English)"),
		SourceCandidate("MANGAFIRE_PTBR", "MangaFire (Português)"),
		SourceCandidate("MANGADEX", "MangaDex"),
		SourceCandidate("MIHON_123456", "Asura Scans"),
		SourceCandidate("MIHON_123456", "Asura Scans"),
	)

	@Test
	fun exactTitleKeepsSiblingLocalesApart() {
		assertEquals(listOf("MANGAFIRE_EN"), SourceSearchFilter.matchSourceNames("mangafire (english)", candidates))
	}

	@Test
	fun containsMatchSelectsAllLocales() {
		assertEquals(
			listOf("MANGAFIRE_EN", "MANGAFIRE_PTBR"),
			SourceSearchFilter.matchSourceNames("Fire", candidates),
		)
	}

	@Test
	fun mihonSourcesMatchByDisplayTitle() {
		assertEquals(listOf("MIHON_123456"), SourceSearchFilter.matchSourceNames(" asura ", candidates))
	}

	@Test
	fun shortQueryMatchesNoSource() {
		assertTrue(SourceSearchFilter.matchSourceNames("M", candidates).isEmpty())
	}

	@Test
	fun likePatternEscapesWildcards() {
		assertEquals("%100\\%%", likeContainsPattern("100%"))
		assertEquals("%a\\_b\\\\c%", likeContainsPattern("a_b\\c"))
		assertEquals("%'; DROP TABLE manga; --%", likeContainsPattern("'; DROP TABLE manga; --"))
	}

	@Test
	fun searchConditionBindsAllUserInput() {
		val query = LibrarySearchQuery("o'neil", listOf("MANGADEX", "MIHON_1"))
		val condition = librarySearchCondition(query, CATEGORY_MATCH)
		assertFalse(condition.sql, condition.sql.contains("o'neil"))
		assertFalse(condition.sql, condition.sql.contains("MANGADEX"))
		assertEquals(condition.sql.count { it == '?' }, condition.args.size)
		assertEquals(listOf("%o'neil%", "%o'neil%", "MANGADEX", "MIHON_1", "%o'neil%"), condition.args)
	}

	@Test
	fun searchConditionWithoutSources() {
		val condition = librarySearchCondition(LibrarySearchQuery("x"), CATEGORY_MATCH)
		assertFalse(condition.sql, condition.sql.contains("manga.source"))
		assertEquals(listOf("%x%", "%x%", "%x%"), condition.args)
		assertEquals(condition.args.size, condition.sql.count { it == '?' })
	}

	@Test
	fun queryBuilderKeepsArgsBeforeFilterConditions() {
		val search = librarySearchCondition(LibrarySearchQuery("abc", listOf("MANGADEX")), CATEGORY_MATCH)
		val query = MangaQueryBuilder("history") { option ->
			if (option == ListFilterOption.Macro.COMPLETED) "percent >= 1" else null
		}
			.where("history.deleted_at = 0")
			.where(search)
			.filters(setOf(ListFilterOption.Macro.COMPLETED))
			.orderBy("history.updated_at DESC")
			.limit(10)
			.build()
		assertEquals(search.args.size, query.argCount)
		val sql = query.sql
		assertTrue(sql, sql.indexOf(search.sql) < sql.indexOf("percent >= 1"))
		assertEquals(search.args.size, sql.count { it == '?' })
	}

	private companion object {

		const val CATEGORY_MATCH = "EXISTS(SELECT 1 FROM favourite_categories c WHERE c.title LIKE ? ESCAPE '\\')"
	}
}
