package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.Page

/**
 * Base source interface for Tachiyomi extensions.
 * This is a stub implementation - extensions will provide the actual implementation.
 */
interface Source {
	/**
	 * Unique identifier for the source.
	 */
	val id: Long

	/**
	 * Display name of the source.
	 */
	val name: String

	/**
	 * Language code of the source.
	 */
	val lang: String
		get() = ""

	/**
	 * Fetch manga details.
	 */
	fun fetchMangaDetails(manga: SManga): rx.Observable<SManga>

	/**
	 * Fetch chapter list for a manga.
	 */
	fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>>

	/**
	 * Fetch page list for a chapter.
	 */
	fun fetchPageList(chapter: SChapter): rx.Observable<List<Page>>
}
