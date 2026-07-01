package io.github.landwarderer.futon.download.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ItemDownloadQueueBinding

class DownloadQueueAdapter(
    private val onRemove: (DownloadQueueItem) -> Unit,
    private val onPauseToggle: (DownloadQueueItem) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : ListAdapter<DownloadQueueItem, DownloadQueueAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDownloadQueueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DownloadQueueItem) {
            binding.textViewTitle.text = item.manga?.title ?: "Unknown"
            binding.textViewDetails.text = "${item.entity.chaptersIds.size} chapters"
            
            binding.imageViewCover.setImageAsync(item.manga?.coverUrl, item.manga)
            
            binding.buttonRemove.setOnClickListener { onRemove(item) }
            binding.buttonPause.setOnClickListener { onPauseToggle(item) }
            binding.buttonPause.setImageResource(
                if (item.entity.isPaused) R.drawable.ic_action_resume else R.drawable.ic_action_pause
            )
            binding.imageViewHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DownloadQueueItem>() {
        override fun areItemsTheSame(oldItem: DownloadQueueItem, newItem: DownloadQueueItem): Boolean {
            return oldItem.entity.id == newItem.entity.id
        }

        override fun areContentsTheSame(oldItem: DownloadQueueItem, newItem: DownloadQueueItem): Boolean {
            return oldItem == newItem
        }
    }
}
