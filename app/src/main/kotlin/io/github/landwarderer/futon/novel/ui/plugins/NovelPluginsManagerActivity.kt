package io.github.landwarderer.futon.novel.ui.plugins

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ActivityNovelPluginsManagerBinding

@AndroidEntryPoint
class NovelPluginsManagerActivity : BaseActivity<ActivityNovelPluginsManagerBinding>() {

    private val viewModel by viewModels<NovelPluginsManagerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityNovelPluginsManagerBinding.inflate(layoutInflater))

        setTitle(R.string.novel_plugins_manager)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

        val adapter = NovelPluginsAdapter(
            onInstallClick = { plugin -> viewModel.installPlugin(plugin) },
            onUninstallClick = { plugin -> viewModel.uninstallPlugin(plugin) },
        )

        viewBinding.recyclerView.adapter = adapter

        viewBinding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPlugins()
        }

        viewBinding.errorState.buttonRetry.setOnClickListener {
            viewModel.loadPlugins()
        }

        viewModel.state.observe(this) { state ->
            viewBinding.loadingState.root.isVisible = state.isLoading && state.items.isEmpty()
            viewBinding.swipeRefresh.isRefreshing = state.isLoading && state.items.isNotEmpty()
            viewBinding.errorState.root.isVisible = state.error != null && state.items.isEmpty()
            adapter.installingIds = state.installingIds
            adapter.submitList(state.items)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is NovelPluginEvent.ShowMessage -> {
                            val msg = if (event.formatArg != null) {
                                getString(event.messageRes, event.formatArg)
                            } else {
                                getString(event.messageRes)
                            }
                            Snackbar.make(viewBinding.root, msg, Snackbar.LENGTH_SHORT).show()
                        }
                        is NovelPluginEvent.ShowError -> {
                            Snackbar.make(viewBinding.root, event.messageRes, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        addMenuProvider(PluginSearchMenuProvider())
    }

    override fun onDestroy() {
        viewBinding.swipeRefresh.clearAnimation()
        viewBinding.swipeRefresh.isRefreshing = false
        viewBinding.loadingState.root.isVisible = false
        viewBinding.recyclerView.adapter = null
        super.onDestroy()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        viewBinding.appbar.updatePadding(
            left = systemBars.left,
            top = systemBars.top,
            right = systemBars.right,
        )
        viewBinding.recyclerView.updatePadding(
            left = systemBars.left,
            right = systemBars.right,
            bottom = systemBars.bottom,
        )
        return insets
    }

    private inner class PluginSearchMenuProvider :
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

        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            (item.actionView as SearchView).setQuery("", false)
            viewModel.filter("")
            return true
        }

        override fun onQueryTextSubmit(query: String?): Boolean = false

        override fun onQueryTextChange(newText: String?): Boolean {
            viewModel.filter(newText.orEmpty())
            return true
        }
    }
}

