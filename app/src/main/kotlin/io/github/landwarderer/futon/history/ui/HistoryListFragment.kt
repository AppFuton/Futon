package io.github.landwarderer.futon.history.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.ui.dialog.buildAlertDialog
import io.github.landwarderer.futon.core.ui.list.ListSelectionController
import io.github.landwarderer.futon.core.ui.list.RecyclerScrollKeeper
import io.github.landwarderer.futon.core.ui.util.MenuInvalidator
import io.github.landwarderer.futon.core.util.ext.addMenuProvider
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.FragmentListBinding
import io.github.landwarderer.futon.databinding.ViewHistoryViewsBinding
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.history.ui.views.HistoryViewsDelegate
import io.github.landwarderer.futon.history.ui.views.HistoryViewsViewModel
import io.github.landwarderer.futon.list.ui.MangaListFragment
import io.github.landwarderer.futon.list.ui.size.DynamicItemSizeResolver
import io.github.landwarderer.futon.main.ui.owners.AppBarOwner
import io.github.landwarderer.futon.main.ui.owners.BottomNavOwner
import javax.inject.Inject

@AndroidEntryPoint
class HistoryListFragment : MangaListFragment() {

	override val viewModel by viewModels<HistoryListViewModel>()
	override val isSwipeRefreshEnabled = false

	private val viewsViewModel by viewModels<HistoryViewsViewModel>()
	private var viewsDelegate: HistoryViewsDelegate? = null
	private var isModeBarVisible = true

	@Inject
	lateinit var spineCropAnalyzer: SpineCropAnalyzer

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		RecyclerScrollKeeper(binding.recyclerView).attach()
		addMenuProvider(HistoryListMenuProvider(binding.root.context, router, viewModel))
		viewModel.isStatsEnabled.observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
		viewsDelegate = HistoryViewsDelegate(
			binding = ViewHistoryViewsBinding.inflate(layoutInflater, binding.root, true),
			listView = binding.swipeRefreshLayout,
			viewModel = viewsViewModel,
			spineCropAnalyzer = spineCropAnalyzer,
			lifecycleOwner = viewLifecycleOwner,
			onOpen = { router.openDetails(it) },
			onBarVisibilityChanged = { isVisible ->
				isModeBarVisible = isVisible
				updateListTopPadding()
			},
			appBarScrollRange = { (activity as? AppBarOwner)?.appBar?.totalScrollRange ?: 0 },
			onFixedViewShown = {
				(activity as? AppBarOwner)?.appBar?.setExpanded(true, true)
				(activity as? BottomNavOwner)?.bottomNav?.animate()?.translationY(0f)
			},
		).also { it.attach(viewModel.isSearching) }
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val result = super.onApplyWindowInsets(v, insets)
		updateListTopPadding()
		viewsDelegate?.setBottomInset(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
		return result
	}

	override fun onDestroyView() {
		viewsDelegate = null
		super.onDestroyView()
	}

	private fun updateListTopPadding() {
		val base = resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		val bar = if (isModeBarVisible) resources.getDimensionPixelOffset(R.dimen.history_views_bar_height) else 0
		viewBinding?.recyclerView?.updatePadding(top = base + bar)
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() = viewModel.clearFilter()

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_history, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				viewModel.removeFromHistory(selectedItemsIds)
				mode?.finish()
				true
			}

			R.id.action_mark_current -> {
				val itemsSnapshot = selectedItems
				buildAlertDialog(context ?: return false, isCentered = true) {
					setTitle(item.title)
					setIcon(item.icon)
					setMessage(R.string.mark_as_completed_prompt)
					setNegativeButton(android.R.string.cancel, null)
					setPositiveButton(android.R.string.ok) { _, _ ->
						viewModel.markAsRead(itemsSnapshot)
						mode?.finish()
					}
				}.show()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	override fun onCreateAdapter() = HistoryListAdapter(
		this,
		DynamicItemSizeResolver(resources, viewLifecycleOwner, settings, adjustWidth = false),
	)
}
