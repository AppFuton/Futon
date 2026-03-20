package io.github.landwarderer.futon.settings.sources.catalog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.landwarderer.futon.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.MangaParserSource

sealed interface SourceCatalogItem : ListModel {

	data class Source(
		val source: MangaParserSource,
	) : SourceCatalogItem {

		override fun areItemsTheSame(other: ListModel): Boolean {
			return other is Source && other.source == source
		}
	}

	data class Hint(
		@DrawableRes val icon: Int,
		@StringRes val title: Int,
		@StringRes val text: Int,
	) : SourceCatalogItem {

		override fun areItemsTheSame(other: ListModel): Boolean {
			return other is Hint && other.title == title
		}
	}
}
