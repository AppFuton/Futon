package io.github.landwarderer.futon.settings.sources.extension

import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseListAdapter
import io.github.landwarderer.futon.databinding.ItemExtensionBinding
import io.github.landwarderer.futon.databinding.ItemExtensionRepoBinding
import io.github.landwarderer.futon.databinding.ItemExtensionRepoHeaderBinding
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.adapter.listHeaderAD
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepo

class ExtensionDownloaderAdapter(
    onAddRepoClick: (String) -> Unit,
    onDeleteRepoClick: (ExternalExtensionRepo) -> Unit,
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
    onUninstallClick: (ExtensionItem) -> Unit,
) : BaseListAdapter<ListModel>() {

    init {
        addDelegate(ListItemType.EXTENSION_REPO_HEADER, repoHeaderItemAD(onAddRepoClick))
        addDelegate(ListItemType.EXTENSION_REPO_ITEM, repoItemAD(onDeleteRepoClick))
        addDelegate(ListItemType.HEADER, listHeaderAD(null))
        addDelegate(ListItemType.EXTENSION, extensionItemAD(onInstallClick, onCancelClick, onUninstallClick))
    }
}

private fun repoHeaderItemAD(
    onAddClick: (String) -> Unit,
) = adapterDelegateViewBinding<RepoHeaderItem, ListModel, ItemExtensionRepoHeaderBinding>(
    { layoutInflater, parent -> ItemExtensionRepoHeaderBinding.inflate(layoutInflater, parent, false) }
) {
    binding.buttonAdd.setOnClickListener {
        val text = binding.editTextUrl.text?.toString().orEmpty()
        if (text.isNotBlank()) {
            onAddClick(text)
            binding.editTextUrl.setText("")
        }
    }

    binding.editTextUrl.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            val text = binding.editTextUrl.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                onAddClick(text)
                binding.editTextUrl.setText("")
            }
            true
        } else {
            false
        }
    }

    bind {
        binding.textViewEmpty.isVisible = !item.hasRepos
    }
}

private fun repoItemAD(
    onDeleteClick: (ExternalExtensionRepo) -> Unit,
) = adapterDelegateViewBinding<RepoItem, ListModel, ItemExtensionRepoBinding>(
    { layoutInflater, parent -> ItemExtensionRepoBinding.inflate(layoutInflater, parent, false) }
) {
    binding.buttonDelete.setOnClickListener {
        onDeleteClick(item.repo)
    }

    bind {
        binding.textViewName.text = item.repo.displayName
        binding.textViewUrl.text = item.repo.baseUrl
        if (item.repo.lastError != null) {
            binding.textViewError.text = item.repo.lastError
            binding.textViewError.isVisible = true
        } else {
            binding.textViewError.isVisible = false
        }
    }
}

private fun extensionItemAD(
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
    onUninstallClick: (ExtensionItem) -> Unit,
) = adapterDelegateViewBinding<ExtensionItem, ListModel, ItemExtensionBinding>(
    { layoutInflater, parent -> ItemExtensionBinding.inflate(layoutInflater, parent, false) }
) {
    binding.buttonAction.setOnClickListener {
        if (item.downloadState != null) {
            onCancelClick(item)
        } else {
            onInstallClick(item)
        }
    }

    binding.buttonUninstall.setOnClickListener {
        onUninstallClick(item)
    }

    binding.root.setOnLongClickListener {
        if (item.isInstalled) {
            onUninstallClick(item)
            true
        } else {
            false
        }
    }

    bind {
        binding.textViewTitle.text = item.available.name
        binding.textViewVersion.text = item.available.versionName
        binding.imageViewIcon.setImageAsync(item.available.iconUrl)

        val downloadState = item.downloadState
        if (downloadState != null) {
            binding.buttonAction.text = context.getString(android.R.string.cancel)
            binding.buttonAction.isVisible = true
            binding.buttonAction.isEnabled = true
            binding.buttonUninstall.isVisible = false
            
            binding.progressBar.isVisible = true
            val progress = downloadState.progressPercent
            if (progress != null) {
                binding.progressBar.isIndeterminate = false
                binding.progressBar.progress = progress
            } else {
                binding.progressBar.isIndeterminate = true
            }
        } else {
            binding.progressBar.isVisible = false
            
            val hasUpdate = item.hasUpdate
            val isInstalled = item.isInstalled
            
            binding.buttonAction.isVisible = !isInstalled || hasUpdate
            if (binding.buttonAction.isVisible) {
                binding.buttonAction.text = if (hasUpdate) context.getString(R.string.update) else context.getString(R.string.install)
                binding.buttonAction.isEnabled = true
            }
            
            binding.buttonUninstall.isVisible = isInstalled
        }
    }
}
