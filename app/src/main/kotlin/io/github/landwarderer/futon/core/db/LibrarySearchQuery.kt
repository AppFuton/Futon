package io.github.landwarderer.futon.core.db

/**
 * Text filtering a library screen (History, Favourites), together with the source names it was
 * resolved to by [SourceSearchFilter].
 */
data class LibrarySearchQuery(
	val text: String,
	val sourceNames: List<String> = emptyList(),
) {

	fun isEmpty() = text.isEmpty()

	companion object {

		val EMPTY = LibrarySearchQuery("")
	}
}

/** An SQL fragment with the arguments bound to its `?` placeholders, in order. */
class SqlCondition(
	val sql: String,
	val args: List<Any>,
)

/** `%text%` with LIKE wildcards in [text] escaped, for use with `ESCAPE '\'`. */
fun likeContainsPattern(text: String): String = buildString(text.length + 2) {
	append('%')
	for (c in text) {
		if (c == '\\' || c == '%' || c == '_') {
			append('\\')
		}
		append(c)
	}
	append('%')
}

/**
 * Matches anything that identifies an entry on a library screen: its title, the source it came
 * from, and the favourites list it is filed under. All user text is bound, never inlined.
 *
 * @param categoryMatch a predicate on the entry's favourites lists with a single `?` placeholder
 * for the LIKE pattern, e.g. `EXISTS(... c.title LIKE ? ESCAPE '\')`
 */
fun librarySearchCondition(query: LibrarySearchQuery, categoryMatch: String): SqlCondition {
	require(categoryMatch.count { it == '?' } == 1) { "categoryMatch must have exactly one placeholder" }
	val pattern = likeContainsPattern(query.text)
	val args = ArrayList<Any>(3 + query.sourceNames.size)
	val sql = StringBuilder("(manga.title LIKE ? ESCAPE '\\' OR manga.alt_title LIKE ? ESCAPE '\\'")
	args += pattern
	args += pattern
	if (query.sourceNames.isNotEmpty()) {
		query.sourceNames.joinTo(sql, prefix = " OR manga.source IN (", postfix = ")") { "?" }
		args.addAll(query.sourceNames)
	}
	sql.append(" OR ").append(categoryMatch).append(')')
	args += pattern
	return SqlCondition(sql.toString(), args)
}
