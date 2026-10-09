package io.github.landwarderer.futon.novel.ui.local

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.LocalNovelSource
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.util.ext.addMenuProvider
import io.github.landwarderer.futon.core.util.ext.withArgs
import io.github.landwarderer.futon.databinding.FragmentListBinding
import io.github.landwarderer.futon.filter.ui.FilterCoordinator
import io.github.landwarderer.futon.list.ui.MangaListFragment
import io.github.landwarderer.futon.remotelist.ui.MangaSearchMenuProvider
import io.github.landwarderer.futon.remotelist.ui.RemoteListFragment

@AndroidEntryPoint
class NovelLocalListFragment : MangaListFragment(), FilterCoordinator.Owner, MenuProvider {

	private val openDocumentLauncher = registerForActivityResult(
		ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (!uris.isNullOrEmpty()) {
			viewModel.importFiles(uris)
		}
	}

	override val viewModel by viewModels<NovelLocalListViewModel>()

	override val filterCoordinator: FilterCoordinator
		get() = viewModel.filterCoordinator

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = arguments ?: Bundle(1)
		args.putString(RemoteListFragment.ARG_SOURCE, LocalNovelSource.name)
		arguments = args
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		addMenuProvider(this)
		addMenuProvider(MangaSearchMenuProvider(filterCoordinator, viewModel))
	}

	override fun onScrolledToEnd() = Unit

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menu.add(Menu.NONE, MENU_ID_IMPORT, Menu.NONE, R.string.novel_import_file).apply {
			setIcon(R.drawable.ic_download)
			setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return if (menuItem.itemId == MENU_ID_IMPORT) {
			openFilePicker()
			true
		} else {
			false
		}
	}

	override fun onEmptyActionClick() {
		openFilePicker()
	}

	override fun onFilterClick(view: View?) {
		router.showFilterSheet()
	}

	companion object {
		private const val MENU_ID_IMPORT = 1001

		fun newInstance() = NovelLocalListFragment().withArgs(1) {
			putString(RemoteListFragment.ARG_SOURCE, LocalNovelSource.name)
		}
	}

	private fun openFilePicker() {
		openDocumentLauncher.launch(
			arrayOf(
				"application/epub+zip",
				"application/pdf",
				"application/octet-stream",
				"*/*",
			)
		)
	}
}
