package io.github.landwarderer.futon.settings.sources.extension

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.databinding.ActivityExtensionDownloaderBinding
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepo

@AndroidEntryPoint
class ExtensionDownloaderActivity : BaseActivity<ActivityExtensionDownloaderBinding>() {

    private val viewModel by viewModels<ExtensionDownloaderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityExtensionDownloaderBinding.inflate(layoutInflater))
        
        setTitle(R.string.extensions_manager)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

        val adapter = ExtensionDownloaderAdapter(
            onAddRepoClick = { url -> viewModel.addRepo(url) },
            onDeleteRepoClick = { repo -> showDeleteRepoDialog(repo) },
            onInstallClick = { viewModel.installExtension(it.available) },
            onCancelClick = { viewModel.cancelDownload(it.available.pkgName) },
            onUninstallClick = { viewModel.uninstallExtension(it.available.pkgName) },
        )

        viewBinding.recyclerView.adapter = adapter

        viewModel.state.observe(this) { state ->
            viewBinding.loadingState.root.isVisible = state.isLoading && state.items.isEmpty()
            adapter.items = state.items
        }

        viewModel.intentAction.observeEvent(this) { intent ->
            startActivity(intent)
        }

        viewModel.onRepoEvent.observeEvent(this) { event ->
            when (event) {
                is RepoEvent.Success -> {
                    Snackbar.make(viewBinding.root, R.string.extension_repo_add_success, Snackbar.LENGTH_SHORT).show()
                }
                is RepoEvent.Error -> {
                    Snackbar.make(viewBinding.root, event.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        addMenuProvider(ExtensionManagerMenuProvider())
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val repoUrl = data.getQueryParameter("url")
            ?: data.getQueryParameter("repo")
            ?: data.getQueryParameter("index")
            ?: return

        if (repoUrl.isNotBlank()) {
            viewModel.addRepo(repoUrl)
        }
    }

    private fun showDeleteRepoDialog(repo: ExternalExtensionRepo) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.extension_repo_remove_confirm, repo.displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteRepo(repo)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(bottom = systemBars.bottom)
        return insets
    }

    private inner class ExtensionManagerMenuProvider :
        MenuProvider,
        MenuItem.OnActionExpandListener,
        SearchView.OnQueryTextListener {

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.opt_extensions, menu)
            val searchMenuItem = menu.findItem(R.id.action_search)
            searchMenuItem.setOnActionExpandListener(this)
            val searchView = searchMenuItem.actionView as SearchView
            searchView.setOnQueryTextListener(this)
            searchView.setIconifiedByDefault(false)
            searchView.queryHint = searchMenuItem.title
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
            return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            (item.actionView as SearchView).setQuery("", false)
            return true
        }

        override fun onQueryTextSubmit(query: String?): Boolean = false

        override fun onQueryTextChange(newText: String?): Boolean {
            viewModel.performSearch(newText)
            return true
        }
    }
}
