package io.github.landwarderer.futon.settings.sources.extension

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.databinding.ActivityExtensionDownloaderBinding

@AndroidEntryPoint
class ExtensionDownloaderActivity : BaseActivity<ActivityExtensionDownloaderBinding>() {

    private val viewModel by viewModels<ExtensionDownloaderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityExtensionDownloaderBinding.inflate(layoutInflater))
        
        setTitle(R.string.extensions_manager)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

        val adapter = ExtensionDownloaderAdapter(
            onInstallClick = { viewModel.installExtension(it.available) },
            onCancelClick = { viewModel.cancelDownload(it.available.pkgName) },
            onUninstallClick = { viewModel.uninstallExtension(it.available.pkgName) }
        )

        viewBinding.recyclerView.adapter = adapter

        viewModel.state.observe(this) { state ->
            viewBinding.loadingState.root.isVisible = state.isLoading && state.items.isEmpty()
            adapter.items = state.items
        }

        viewModel.intentAction.observeEvent(this) { intent ->
            startActivity(intent)
        }

        addMenuProvider(ExtensionManagerMenuProvider())
    }

    override fun onApplyWindowInsets(v: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat {
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
