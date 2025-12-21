package io.github.landwarderer.futon.favourites.domain.model

import io.github.landwarderer.futon.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
