package io.github.landwarderer.futon.core.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class ContentTypeConditionTest {

	private val manhwaSource = MangaParserSource.entries.first { it.contentType == ContentType.MANHWA }
	private val mangaSource = MangaParserSource.entries.first { it.contentType == ContentType.MANGA }

	@Test
	fun parserSourcesAreSelectedByDeclaredType() {
		val sql = contentTypeCondition(ContentType.MANHWA, listOf(manhwaSource, mangaSource))
		assertTrue(sql, sql.contains("manga.source IN ('${manhwaSource.name}')"))
		assertFalse(sql, sql.contains("'${mangaSource.name}'"))
	}

	@Test
	fun mihonSourcesAreSelectedByTag() {
		val sql = contentTypeCondition(ContentType.MANHWA, listOf(manhwaSource))
		assertTrue(sql, sql.contains("manga.source LIKE 'MIHON\\_%' ESCAPE '\\'"))
		assertTrue(sql, sql.contains("LOWER(tags.title) IN ('manhwa')"))
		// Both branches are OR'ed together and wrapped so they cannot leak into the surrounding AND.
		assertTrue(sql, sql.startsWith("(") && sql.endsWith(")"))
		assertTrue(sql, sql.contains(") OR ("))
	}

	@Test
	fun onlyMihonBranchWhenNoParserSourceHasTheType() {
		val sql = contentTypeCondition(ContentType.MANHUA, listOf(mangaSource))
		assertFalse(sql, sql.contains("manga.source IN"))
		assertTrue(sql, sql.contains("IN ('manhua')"))
	}

	@Test
	fun matchesNothingWithoutAnyWayToTell() {
		assertEquals("0", contentTypeCondition(ContentType.NOVEL, listOf(mangaSource)))
	}

	@Test
	fun quoteEscapesSingleQuotes() {
		assertEquals("'it''s'", sqlQuote("it's"))
	}
}
