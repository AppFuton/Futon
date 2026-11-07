package io.github.landwarderer.futon.novel.ui.plugins

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ItemNovelPluginBinding
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin

class NovelPluginsAdapter(
    private val onInstallClick: (LnPlugin) -> Unit,
    private val onUninstallClick: (LnPlugin) -> Unit,
) : ListAdapter<LnPlugin, NovelPluginsAdapter.ViewHolder>(DiffCallback) {

    var installingIds: Set<String> = emptySet()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNovelPluginBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.imageViewIcon.disposeImage()
        holder.binding.progressBar.isVisible = false
    }

    inner class ViewHolder(
        private val binding: ItemNovelPluginBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plugin: LnPlugin) {
            val context = binding.root.context
            val isInstalling = plugin.id in installingIds

            binding.textViewTitle.text = plugin.name
            binding.textViewSubtitle.text = "${plugin.lang} • v${plugin.version}"
            binding.imageViewIcon.setImageAsync(plugin.iconUrl)

            binding.progressBar.isVisible = isInstalling

            if (isInstalling) {
                binding.buttonAction.isEnabled = false
                binding.buttonUninstall.isEnabled = false
            } else {
                binding.buttonAction.isEnabled = true
                binding.buttonUninstall.isEnabled = true

                when {
                    plugin.hasUpdate -> {
                        binding.buttonAction.isVisible = true
                        binding.buttonAction.text = context.getString(R.string.update)
                        binding.buttonUninstall.isVisible = true
                    }
                    plugin.isInstalled -> {
                        binding.buttonAction.isVisible = true
                        binding.buttonAction.text = context.getString(R.string.plugin_installed)
                        binding.buttonAction.isEnabled = false
                        binding.buttonUninstall.isVisible = true
                    }
                    else -> {
                        binding.buttonAction.isVisible = true
                        binding.buttonAction.text = context.getString(R.string.install)
                        binding.buttonUninstall.isVisible = false
                    }
                }
            }

            binding.buttonAction.setOnClickListener {
                if (!isInstalling) {
                    onInstallClick(plugin)
                }
            }

            binding.buttonUninstall.setOnClickListener {
                if (!isInstalling) {
                    onUninstallClick(plugin)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LnPlugin>() {
        override fun areItemsTheSame(oldItem: LnPlugin, newItem: LnPlugin): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LnPlugin, newItem: LnPlugin): Boolean {
            return oldItem == newItem
        }
    }
}

