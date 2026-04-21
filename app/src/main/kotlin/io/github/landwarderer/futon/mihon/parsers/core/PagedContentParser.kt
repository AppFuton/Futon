package io.github.landwarderer.futon.mihon.parsers.core

import androidx.annotation.VisibleForTesting
import io.github.landwarderer.futon.mihon.parsers.ContentLoaderContext
import io.github.landwarderer.futon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.SortOrder
import io.github.landwarderer.futon.mihon.parsers.util.Paginator

@InternalParsersApi
public abstract class PagedContentParser(
	context: ContentLoaderContext,
	source: ContentSource,
	@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED) @JvmField public val pageSize: Int,
	searchPageSize: Int = pageSize,
) : AbstractContentParser(context, source) {

	@JvmField
	protected val paginator: Paginator = Paginator(pageSize)

	@JvmField
	protected val searchPaginator: Paginator = Paginator(searchPageSize)

	final override suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content> {
		return getList(
			paginator = if (filter.query.isNullOrEmpty()) {
				paginator
			} else {
				searchPaginator
			},
			offset = offset,
			order = order,
			filter = filter,
		)
	}

	public abstract suspend fun getListPage(page: Int, order: SortOrder, filter: ContentListFilter): List<Content>

	protected fun setFirstPage(firstPage: Int, firstPageForSearch: Int = firstPage) {
		paginator.firstPage = firstPage
		searchPaginator.firstPage = firstPageForSearch
	}

	private suspend fun getList(
		paginator: Paginator,
		offset: Int,
		order: SortOrder,
		filter: ContentListFilter,
	): List<Content> {
		val page = paginator.getPage(offset)
		val list = getListPage(page, order, filter)
		paginator.onListReceived(offset, page, list.size)
		return list
	}
}

