package io.github.landwarderer.futon.download.ui.list

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.list.ListSelectionController
import io.github.landwarderer.futon.core.ui.list.RecyclerScrollKeeper
import io.github.landwarderer.futon.core.ui.util.MenuInvalidator
import io.github.landwarderer.futon.core.ui.util.ReversibleActionObserver
import io.github.landwarderer.futon.core.util.FileSize
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.databinding.ActivityDownloadsBinding
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.list.ui.adapter.TypedListSpacingDecoration
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsActivity : BaseActivity<ActivityDownloadsBinding>(),
	DownloadItemListener,
	ListSelectionController.Callback {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var scheduler: DownloadWorker.Scheduler

	private val viewModel by viewModels<DownloadsViewModel>()
	private lateinit var selectionController: ListSelectionController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDownloadsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val downloadsAdapter = DownloadsAdapter(this, this)
		val decoration = TypedListSpacingDecoration(this, false)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = DownloadsSelectionDecoration(this),
			registryOwner = this,
			callback = this,
		)
		with(viewBinding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(decoration)
			adapter = downloadsAdapter
			selectionController.attachToRecyclerView(this)
			RecyclerScrollKeeper(this).attach()
		}
		addMenuProvider(DownloadsMenuProvider(this, viewModel))
		viewModel.items.observe(this, downloadsAdapter)
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.recyclerView))
		val menuInvalidator = MenuInvalidator(this)
		viewModel.hasActiveWorks.observe(this, menuInvalidator)
		viewModel.hasPausedWorks.observe(this, menuInvalidator)
		viewModel.hasCancellableWorks.observe(this, menuInvalidator)

		viewModel.storageUsage.observe(this) { usage ->
			if (usage != null && usage.totalBytes > 0) {
				viewBinding.cardStorage.isVisible = true
				val progress = (usage.currentBytes * 100 / usage.totalBytes).toInt()
				viewBinding.progressStorage.progress = progress
				val isOverQuota = usage.currentBytes >= usage.totalBytes
				if (viewBinding.cardQuotaReached.isVisible != isOverQuota) {
					viewBinding.cardQuotaReached.isVisible = isOverQuota
				}
				if (progress >= 90) {
					viewBinding.progressStorage.setIndicatorColor(getColor(R.color.common_red))
				} else {
					viewBinding.progressStorage.setIndicatorColor(getColor(R.color.blue_primary))
				}
				val currentStr = FileSize.BYTES.format(this, usage.currentBytes)
				val totalStr = FileSize.BYTES.format(this, usage.totalBytes)
				viewBinding.textViewStorageDetails.text = getString(R.string.memory_usage_pattern, currentStr, totalStr)
			} else {
				viewBinding.cardStorage.isVisible = false
				viewBinding.cardQuotaReached.isVisible = false
			}
		}
		viewModel.refreshStorageUsage()
	}

	override fun onResume() {
		super.onResume()
		viewModel.refreshStorageUsage()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.recyclerView.updatePadding(
			left = bars.left,
			right = bars.right,
			bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.list_spacing_large),
		)
		viewBinding.cardStorage.updatePadding(
			bottom = bars.bottom
		)
		viewBinding.appbar.updatePadding(
			left = bars.left,
			right = bars.right,
			top = bars.top,
		)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	override fun onItemClick(item: DownloadItemModel, view: View) {
		if (selectionController.onItemClick(item.id.mostSignificantBits)) {
			return
		}
		router.openDetails(item.manga ?: return)
	}

	override fun onItemLongClick(item: DownloadItemModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id.mostSignificantBits)
	}

	override fun onItemContextClick(item: DownloadItemModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id.mostSignificantBits)
	}

	override fun onExpandClick(item: DownloadItemModel) {
		if (!selectionController.onItemClick(item.id.mostSignificantBits)) {
			viewModel.expandCollapse(item)
		}
	}

	override fun onCancelClick(item: DownloadItemModel) {
		viewModel.cancel(item.id)
	}

	override fun onPauseClick(item: DownloadItemModel) {
		scheduler.pause(item.id)
	}

	override fun onResumeClick(item: DownloadItemModel) {
		scheduler.resume(item.id)
	}

	override fun onSkipClick(item: DownloadItemModel) {
		scheduler.skip(item.id)
	}

	override fun onSkipAllClick(item: DownloadItemModel) {
		scheduler.skipAll(item.id)
	}

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_downloads, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_resume -> {
				viewModel.resume(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_pause -> {
				viewModel.pause(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_cancel -> {
				viewModel.cancel(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_remove -> {
				viewModel.remove(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_select_all -> {
				controller.addAll(viewModel.allIds())
				true
			}

			else -> false
		}
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val snapshot = viewModel.snapshot(controller.peekCheckedIds())
		var canPause = true
		var canResume = true
		var canCancel = true
		var canRemove = true
		for (item in snapshot) {
			canPause = canPause and item.canPause
			canResume = canResume and item.canResume
			canCancel = canCancel and !item.workState.isFinished
			canRemove = canRemove and item.workState.isFinished
		}
		menu.findItem(R.id.action_pause)?.isVisible = canPause
		menu.findItem(R.id.action_resume)?.isVisible = canResume
		menu.findItem(R.id.action_cancel)?.isVisible = canCancel
		menu.findItem(R.id.action_remove)?.isVisible = canRemove
		return super.onPrepareActionMode(controller, mode, menu)
	}
}
