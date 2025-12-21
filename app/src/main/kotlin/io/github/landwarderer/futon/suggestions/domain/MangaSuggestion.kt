package io.github.landwarderer.futon.suggestions.domain

import androidx.annotation.FloatRange
import io.github.landwarderer.futon.parsers.model.Manga

data class MangaSuggestion(
	val manga: Manga,
	@FloatRange(from = 0.0, to = 1.0)
	val relevance: Float,
)