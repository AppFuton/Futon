package io.github.landwarderer.futon.settings.sources.catalog

import io.github.landwarderer.futon.parsers.model.ContentType

data class SourcesCatalogFilter(
	val types: Set<ContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
)
