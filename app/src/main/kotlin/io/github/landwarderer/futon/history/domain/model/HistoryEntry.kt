package io.github.landwarderer.futon.history.domain.model

import org.koitharu.kotatsu.parsers.model.Manga

data class ReadingSession(
	val startedAt: Long,
	val duration: Long,
	val pages: Int,
)

data class HistoryEntry(
	val manga: Manga,
	val percent: Float,
	val updatedAt: Long,
	val chaptersCount: Int,
	val sessions: List<ReadingSession>,
)
