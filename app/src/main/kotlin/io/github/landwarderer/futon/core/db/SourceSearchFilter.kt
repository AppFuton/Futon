package io.github.landwarderer.futon.core.db

import dagger.Reusable
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Resolves search text to the source names stored in `manga.source`.
 *
 * The column holds identifiers (`MANGAFIRE_EN` for a built-in parser, `MIHON_<id>` for a Mihon
 * extension) while everything the user sees - the search bar, the suggestion row - carries the
 * display title ("MangaFire (English)", "Asura Scans"). The two are not derivable from one another,
 * so the text is matched against the real source list and the exact names are emitted.
 *
 * Mihon titles come from the installed extensions and, for extensions that have since been
 * uninstalled, from the titles persisted in the `sources` table.
 */
@Reusable
class SourceSearchFilter @Inject constructor(
	private val db: MangaDatabase,
	private val mihonExtensionManager: MihonExtensionManager,
) {

	/** The screen filter for [text], with the sources it names resolved. */
	suspend fun resolve(text: String): LibrarySearchQuery = if (text.isEmpty()) {
		LibrarySearchQuery.EMPTY
	} else {
		LibrarySearchQuery(text, findSourceNames(text))
	}

	suspend fun findSourceNames(query: String): List<String> {
		if (query.trim().length < MIN_QUERY_LENGTH) {
			return emptyList()
		}
		val candidates = ArrayList<SourceCandidate>(MangaParserSource.entries.size + 64)
		MangaParserSource.entries.mapTo(candidates) { SourceCandidate(it.name, it.title) }
		mihonExtensionManager.getMihonMangaSources().mapTo(candidates) { SourceCandidate(it.name, it.displayName) }
		runCatchingCancellable {
			db.getSourcesDao().findAll()
		}.getOrNull()?.forEach { entity ->
			val title = entity.title
			if (!title.isNullOrEmpty() && isMihonSourceName(entity.source)) {
				candidates += SourceCandidate(entity.source, title)
			}
		}
		return matchSourceNames(query, candidates)
	}

	data class SourceCandidate(
		val name: String,
		val title: String,
	)

	companion object {

		/** Two characters is where a contains match stops selecting most of the source list. */
		private const val MIN_QUERY_LENGTH = 2

		private const val MAX_SOURCES = 24

		fun isMihonSourceName(name: String) = name.startsWith("MIHON_") || name.startsWith("mihon:")

		/**
		 * An exact title match wins, which is what a tapped suggestion produces and what keeps sibling
		 * locales apart - picking "MangaFire (English)" must not also select `MANGAFIRE_PTBR`. Typed
		 * text falls back to a contains match, where selecting every MangaFire locale is the
		 * reasonable reading.
		 */
		fun matchSourceNames(query: String, candidates: Collection<SourceCandidate>): List<String> {
			val q = query.trim()
			if (q.length < MIN_QUERY_LENGTH) {
				return emptyList()
			}
			val exact = candidates.filter { it.title.equals(q, ignoreCase = true) }
			if (exact.isNotEmpty()) {
				return exact.mapTo(LinkedHashSet()) { it.name }.toList()
			}
			return candidates.asSequence()
				.filter { it.title.contains(q, ignoreCase = true) }
				.map { it.name }
				.distinct()
				.take(MAX_SOURCES)
				.toList()
		}
	}
}
