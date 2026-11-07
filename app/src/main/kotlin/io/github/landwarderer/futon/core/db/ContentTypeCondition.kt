package io.github.landwarderer.futon.core.db

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * SQL predicate selecting library manga of the given [contentType], for a query joined with `manga`.
 *
 * Built-in parsers declare a content type, so their manga are selected by source name. Mihon
 * extensions do not: Futon's wrapper reports every Mihon source as plain manga (or hentai), so a
 * source-based lookup would put every Mihon webtoon under "Manga" and hide it from "Manhwa". For
 * those sources the manga's own genre tags are used instead - many sites tag their entries with
 * "Manhwa" or "Manhua" - and a Mihon manga carrying no such tag is only listed when no content type
 * is selected.
 */
internal fun contentTypeCondition(
	contentType: ContentType,
	parserSources: Collection<MangaParserSource> = MangaParserSource.entries,
): String {
	val conditions = ArrayList<String>(2)
	val sourceNames = parserSources.filter { it.contentType == contentType }
	if (sourceNames.isNotEmpty()) {
		conditions += sourceNames.joinToString(
			prefix = "manga.source IN (",
			postfix = ")",
			transform = { sqlQuote(it.name) },
		)
	}
	val tagTitles = contentTypeTagTitles(contentType)
	if (tagTitles.isNotEmpty()) {
		conditions += "($MIHON_SOURCE_CONDITION AND EXISTS(SELECT 1 FROM manga_tags " +
			"INNER JOIN tags ON tags.tag_id = manga_tags.tag_id WHERE manga_tags.manga_id = manga.manga_id " +
			"AND LOWER(tags.title) IN (${tagTitles.joinToString { sqlQuote(it) }})))"
	}
	return when (conditions.size) {
		0 -> "0"
		1 -> conditions.single()
		else -> conditions.joinToString(separator = " OR ", prefix = "(", postfix = ")")
	}
}

/** Lower-case genre tag titles that mark a manga from a source without a declared content type. */
internal fun contentTypeTagTitles(contentType: ContentType): List<String> = when (contentType) {
	ContentType.MANGA -> listOf("manga")
	ContentType.MANHWA -> listOf("manhwa")
	ContentType.MANHUA -> listOf("manhua")
	else -> emptyList()
}

/** Matches the names Mihon sources are stored under (see `MangaSource(name)`). */
private const val MIHON_SOURCE_CONDITION = "(manga.source LIKE 'MIHON\\_%' ESCAPE '\\' OR manga.source LIKE 'mihon:%')"

/** Quotes a constant as an SQL string literal. Only used for values built into the app. */
internal fun sqlQuote(value: String): String = "'" + value.replace("'", "''") + "'"
