package io.github.landwarderer.futon.alternatives.domain

import org.koitharu.kotatsu.parsers.model.Manga

data class AlternativesSearchOptions(
	val query: String,
	val sourceScope: AlternativeSourceScope = AlternativeSourceScope.Enabled,
	val sameLanguageOnly: Boolean = false,
	val sameContentTypeOnly: Boolean = false,
	val hideNoChapters: Boolean = false,
	val sortOrder: AlternativeSortOrder = AlternativeSortOrder.BEST_MATCH,
) {

	fun isDefaultFor(title: String, defaultScope: AlternativeSourceScope): Boolean =
		this == AlternativesSearchOptions(query = title, sourceScope = defaultScope)
}

/** Which sources an alternatives search or a source replacement runs across. */
sealed interface AlternativeSourceScope {

	data object Enabled : AlternativeSourceScope

	/** The sources the user has not enabled - where an alternative is most often hiding. */
	data object Disabled : AlternativeSourceScope

	/** Any saved preset, not only the active one. */
	data class Preset(val id: Long) : AlternativeSourceScope
}

enum class AlternativeSortOrder {
	BEST_MATCH,
	MOST_CHAPTERS,
	CLOSEST_CHAPTER_COUNT,
	SOURCE_PRIORITY,
}

sealed interface AlternativeSearchEvent {

	data class Result(val manga: Manga) : AlternativeSearchEvent

	data class Progress(
		val completedSources: Int,
		val totalSources: Int,
	) : AlternativeSearchEvent
}

data class AlternativeSearchStatus(
	val completedSources: Int = 0,
	val totalSources: Int = 0,
	val resultsCount: Int = 0,
	val isRunning: Boolean = false,
)

data class AlternativeSourceScopeOptions(
	val defaultScope: AlternativeSourceScope,
	/** Presets that have at least one candidate source, in the user's preset order. */
	val presets: List<PresetChoice> = emptyList(),
	/** False when every candidate is already enabled, or every source is enabled. */
	val hasDisabledSources: Boolean = false,
) {

	data class PresetChoice(val id: Long, val title: String)

	fun isAvailable(scope: AlternativeSourceScope): Boolean = when (scope) {
		AlternativeSourceScope.Enabled -> true
		AlternativeSourceScope.Disabled -> hasDisabledSources
		is AlternativeSourceScope.Preset -> presets.any { it.id == scope.id }
	}

	fun presetTitle(id: Long): String? = presets.firstOrNull { it.id == id }?.title
}
