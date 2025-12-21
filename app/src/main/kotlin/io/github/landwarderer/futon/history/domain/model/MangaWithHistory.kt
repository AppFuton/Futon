package io.github.landwarderer.futon.history.domain.model

import io.github.landwarderer.futon.core.model.MangaHistory
import io.github.landwarderer.futon.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
