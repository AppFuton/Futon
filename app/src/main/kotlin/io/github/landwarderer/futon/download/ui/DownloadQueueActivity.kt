package io.github.landwarderer.futon.download.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ActivityDownloadQueueBinding

@AndroidEntryPoint
class DownloadQueueActivity : BaseActivity<ActivityDownloadQueueBinding>() {

    private val viewModel by viewModels<DownloadQueueViewModel>()
    private lateinit var adapter: DownloadQueueAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityDownloadQueueBinding.inflate(layoutInflater))
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
        setTitle(R.string.download_queue)

        adapter = DownloadQueueAdapter(
            onRemove = { item -> viewModel.removeFromQueue(item.entity.id) },
            onPauseToggle = { item -> viewModel.updatePaused(item.entity.id, !item.entity.isPaused) },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )

        viewBinding.recyclerView.adapter = adapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                val list = adapter.currentList.toMutableList()
                val item = list.removeAt(fromPos)
                list.add(toPos, item)
                adapter.submitList(list)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewModel.reorderQueue(adapter.currentList.map { it.entity.id })
            }
        })
        itemTouchHelper.attachToRecyclerView(viewBinding.recyclerView)

        viewModel.queue.observe(this, adapter::submitList)

        viewBinding.fabStart.setOnClickListener {
            viewModel.triggerScheduler()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.opt_download_queue, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_queue -> {
                viewModel.clearQueue()
                true
            }
            R.id.action_queue_favorites -> {
                viewModel.queueAllUnreadFromFavorites()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val fabMargin = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom_padding)
        
        viewBinding.recyclerView.updatePadding(
            left = bars.left,
            right = bars.right,
            bottom = bars.bottom + fabMargin,
        )
        viewBinding.fabStart.apply {
            val params = layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bars.bottom + resources.getDimensionPixelSize(R.dimen.fab_margin_bottom_padding)
            layoutParams = params
        }
        viewBinding.appbar.updatePadding(
            left = bars.left,
            right = bars.right,
            top = bars.top,
        )
        return insets
    }
}
