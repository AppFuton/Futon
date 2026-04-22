package io.github.landwarderer.futon.settings.sources.extension

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseListAdapter
import io.github.landwarderer.futon.databinding.ItemExtensionBinding
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.model.ListModel

class ExtensionDownloaderAdapter(
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
) : BaseListAdapter<ListModel>() {

    init {
        addDelegate(ListItemType.EXTENSION, extensionItemAD(onInstallClick, onCancelClick))
    }
}

private fun extensionItemAD(
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
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

    bind {
        binding.textViewTitle.text = item.available.name
        binding.textViewVersion.text = item.available.versionName
        binding.imageViewIcon.setImageAsync(item.available.iconUrl)

        val downloadState = item.downloadState
        if (downloadState != null) {
            binding.buttonAction.text = context.getString(android.R.string.cancel)
            // progress can be added here if needed
        } else {
            binding.buttonAction.text = when {
                item.hasUpdate -> context.getString(R.string.update)
                item.isInstalled -> context.getString(R.string.installed)
                else -> context.getString(R.string.install)
            }
            binding.buttonAction.isEnabled = !item.isInstalled || item.hasUpdate
        }
    }
}
