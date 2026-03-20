package io.github.landwarderer.futon.core.model

import org.koitharu.kotatsu.parsers.model.MangaSource

data class MangaSourceInfo(
	val mangaSource: MangaSource,
	val isEnabled: Boolean,
	val isPinned: Boolean,
) : MangaSource by mangaSource
