package io.github.landwarderer.futon.alternatives.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.alternatives.domain.AlternativeSortOrder

class AlternativesMenuProvider(
	private val activity: AlternativesActivity,
	private val viewModel: AlternativesViewModel,
) : MenuProvider, MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_regular_alternatives, menu)
		val searchItem = menu.findItem(R.id.action_search)
		searchItem.setOnActionExpandListener(this)
		(searchItem.actionView as SearchView).run {
			setOnQueryTextListener(this@AlternativesMenuProvider)
			queryHint = activity.getString(R.string.search_manga)
		}
	}

	override fun onPrepareMenu(menu: Menu) {
		val options = viewModel.options.value
		menu.findItem(R.id.action_alternatives_sources)?.subMenu?.let { sourcesMenu ->
			sourcesMenu.clear()
			SourceScopeMenu.populate(sourcesMenu, SOURCES_GROUP, viewModel.scopeOptions.value, options.sourceScope)
		}
		menu.findItem(R.id.action_alternatives_reset)?.isVisible = viewModel.hasCustomOptions()
		menu.findItem(R.id.action_alternatives_same_language)?.isChecked = options.sameLanguageOnly
		menu.findItem(R.id.action_alternatives_same_content_type)?.isChecked = options.sameContentTypeOnly
		menu.findItem(R.id.action_alternatives_hide_no_chapters)?.isChecked = options.hideNoChapters
		menu.findItem(
			when (options.sortOrder) {
				AlternativeSortOrder.BEST_MATCH -> R.id.action_alternatives_sort_best_match
				AlternativeSortOrder.MOST_CHAPTERS -> R.id.action_alternatives_sort_most_chapters
				AlternativeSortOrder.CLOSEST_CHAPTER_COUNT -> R.id.action_alternatives_sort_closest_chapters
				AlternativeSortOrder.SOURCE_PRIORITY -> R.id.action_alternatives_sort_source_priority
			},
		)?.isChecked = true
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		val options = viewModel.options.value
		when (menuItem.itemId) {

			R.id.action_alternatives_same_language -> viewModel.setSameLanguageOnly(!options.sameLanguageOnly)
			R.id.action_alternatives_same_content_type -> viewModel.setSameContentTypeOnly(!options.sameContentTypeOnly)
			R.id.action_alternatives_hide_no_chapters -> viewModel.setHideNoChapters(!options.hideNoChapters)
			R.id.action_alternatives_sort_best_match -> viewModel.setSortOrder(AlternativeSortOrder.BEST_MATCH)
			R.id.action_alternatives_sort_most_chapters -> viewModel.setSortOrder(AlternativeSortOrder.MOST_CHAPTERS)
			R.id.action_alternatives_sort_closest_chapters ->
				viewModel.setSortOrder(AlternativeSortOrder.CLOSEST_CHAPTER_COUNT)

			R.id.action_alternatives_sort_source_priority -> viewModel.setSortOrder(AlternativeSortOrder.SOURCE_PRIORITY)
			R.id.action_alternatives_reset -> viewModel.resetOptions()
			else -> viewModel.setSourceScope(
				SourceScopeMenu.scopeFor(menuItem.itemId, viewModel.scopeOptions.value) ?: return false,
			)
		}
		activity.invalidateMenu()
		return true
	}

	override fun onQueryTextSubmit(query: String?): Boolean {
		query?.takeIf(String::isNotBlank)?.let(viewModel::setQuery)
		return true
	}

	override fun onQueryTextChange(newText: String?): Boolean = false

	override fun onMenuItemActionExpand(item: MenuItem): Boolean {
		(item.actionView as? SearchView)?.post {
			(item.actionView as? SearchView)?.setQuery(viewModel.options.value.query, false)
		}
		return true
	}

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean = true

	private companion object {
		const val SOURCES_GROUP = Menu.FIRST + 4000
	}
}
