package io.github.landwarderer.futon.details.ui.pager.pages

import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage

data class PageThumbnail(
	val isCurrent: Boolean,
	val page: ReaderPage,
) : ListModel {

	val number
		get() = page.index + 1

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is PageThumbnail && page == other.page
	}
}
