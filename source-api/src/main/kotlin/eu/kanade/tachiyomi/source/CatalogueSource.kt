package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage

/**
 * Catalogue source interface for browseable sources.
 * This is a stub implementation - extensions will provide the actual implementation.
 */
interface CatalogueSource : Source {
	/**
	 * Whether the source supports latest updates.
	 */
	val supportsLatest: Boolean
		get() = true

	/**
	 * Fetch popular manga.
	 */
	fun fetchPopularManga(page: Int): rx.Observable<MangasPage>

	/**
	 * Fetch manga search results.
	 */
	fun fetchSearchManga(page: Int, query: String, filters: FilterList): rx.Observable<MangasPage>

	/**
	 * Fetch latest manga updates.
	 */
	fun fetchLatestUpdates(page: Int): rx.Observable<MangasPage>

	/**
	 * Get search filters for this source.
	 */
	fun getFilterList(): FilterList
}
