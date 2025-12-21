package io.github.landwarderer.futon.core.exceptions

import io.github.landwarderer.futon.parsers.model.Manga

class UnsupportedSourceException(
	message: String?,
	val manga: Manga?,
) : IllegalArgumentException(message)
