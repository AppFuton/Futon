package io.github.landwarderer.futon.tracker.ui.updates

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
import io.github.landwarderer.futon.core.ui.list.ListSelectionController
import io.github.landwarderer.futon.databinding.FragmentListBinding
import io.github.landwarderer.futon.databinding.ViewUpdatesViewsBinding
import io.github.landwarderer.futon.list.ui.MangaListFragment

@AndroidEntryPoint
class UpdatesFragment : MangaListFragment() {

	override val viewModel by viewModels<UpdatesViewModel>()
	override val isSwipeRefreshEnabled = false

	private var viewsDelegate: UpdatesViewsDelegate? = null

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		viewsDelegate = UpdatesViewsDelegate(
			binding = ViewUpdatesViewsBinding.inflate(layoutInflater, binding.root, true),
			listView = binding.swipeRefreshLayout,
			activity = requireActivity(),
			lifecycleOwner = viewLifecycleOwner,
			mode = viewModel.viewMode,
			entries = viewModel.mapEntries,
			shelf = viewModel.mapShelf,
			isMinimalTheme = { settings.isMinimalThemeEnabled },
			onModeSelected = viewModel::setViewMode,
			onOpen = { router.openDetails(it) },
		).also { it.attach() }
		updateListTopPadding()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val result = super.onApplyWindowInsets(v, insets)
		updateListTopPadding()
		viewsDelegate?.setSystemInset(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
		return result
	}

	override fun onDestroyView() {
		viewsDelegate = null
		super.onDestroyView()
	}

	override fun onScrolledToEnd() = Unit

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_updates, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				viewModel.remove(controller.snapshot())
				mode?.finish()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	private fun updateListTopPadding() {
		val base = resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		val bar = resources.getDimensionPixelOffset(R.dimen.history_views_bar_height)
		viewBinding?.recyclerView?.updatePadding(top = base + bar)
	}
}
