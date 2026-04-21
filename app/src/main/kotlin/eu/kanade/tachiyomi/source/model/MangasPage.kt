package eu.kanade.tachiyomi.source.model

/**
 * Mihon-compatible MangasPage data class.
 */
data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)
