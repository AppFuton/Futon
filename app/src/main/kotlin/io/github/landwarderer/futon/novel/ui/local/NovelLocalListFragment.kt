package io.github.landwarderer.futon.novel.ui.local

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.LocalNovelSource
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.os.OpenDocumentTreeHelper
import io.github.landwarderer.futon.core.util.ext.addMenuProvider
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.tryLaunch
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

	private val openDirectoryLauncher = OpenDocumentTreeHelper(this) { uri ->
		if (uri != null) {
			try {
				val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				requireContext().contentResolver.takePersistableUriPermission(uri, flags)
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
			viewModel.importFolder(uri)
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
			showImportOptionsDialog()
			true
		} else {
			false
		}
	}

	override fun onEmptyActionClick() {
		showImportOptionsDialog()
	}

	override fun onFilterClick(view: View?) {
		router.showFilterSheet()
	}

	private fun showImportOptionsDialog() {
		val options = arrayOf(
			getString(R.string.add_folder),
			getString(R.string.select_files),
		)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string._import)
			.setItems(options) { _, which ->
				when (which) {
					0 -> if (!openDirectoryLauncher.tryLaunch(null)) {
						Toast.makeText(requireContext(), R.string.operation_not_supported, Toast.LENGTH_SHORT).show()
					}
					1 -> openFilePicker()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
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
