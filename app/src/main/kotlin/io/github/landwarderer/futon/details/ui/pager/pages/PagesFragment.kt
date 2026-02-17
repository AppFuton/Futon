package io.github.landwarderer.futon.details.ui.pager.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.collection.ArraySet
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.exceptions.resolve.SnackbarErrorObserver
import io.github.landwarderer.futon.core.nav.ReaderIntent
import io.github.landwarderer.futon.core.nav.dismissParentDialog
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseFragment
import io.github.landwarderer.futon.core.ui.list.BoundsScrollListener
import io.github.landwarderer.futon.core.ui.list.ListSelectionController
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.futon.core.ui.util.PagerNestedScrollHelper
import io.github.landwarderer.futon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.futon.core.util.RecyclerViewScrollCallback
import io.github.landwarderer.futon.core.util.ext.consumeAll
import io.github.landwarderer.futon.core.util.ext.findAppCompatDelegate
import io.github.landwarderer.futon.core.util.ext.findParentCallback
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.setTextAndVisible
import io.github.landwarderer.futon.core.util.ext.showOrHide
import io.github.landwarderer.futon.databinding.FragmentPagesBinding
import io.github.landwarderer.futon.details.ui.pager.ChaptersPagesViewModel
import io.github.landwarderer.futon.details.ui.pager.EmptyMangaReason
import io.github.landwarderer.futon.list.ui.GridSpanResolver
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.adapter.TypedListSpacingDecoration
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.reader.ui.PageSaveHelper
import io.github.landwarderer.futon.reader.ui.ReaderNavigationCallback
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class PagesFragment :
	BaseFragment<FragmentPagesBinding>(),
	OnListItemClickListener<PageThumbnail>,
	RecyclerViewOwner,
	ListSelectionController.Callback {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var pageSaveHelperFactory: PageSaveHelper.Factory

	private val parentViewModel by ChaptersPagesViewModel.ActivityVMLazy(this)
	private val viewModel by viewModels<PagesViewModel>()
	private lateinit var pageSaveHelper: PageSaveHelper

	private var thumbnailsAdapter: PageThumbnailAdapter? = null
	private var spanResolver: GridSpanResolver? = null
	private var scrollListener: ScrollListener? = null
	private var selectionController: ListSelectionController? = null

	private val spanSizeLookup = SpanSizeLookup()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		pageSaveHelper = pageSaveHelperFactory.create(this)
		combine(
			parentViewModel.mangaDetails,
			parentViewModel.readingState,
			parentViewModel.selectedBranch,
		) { details, readingState, branch ->
			if (details != null && (details.isLoaded || details.chapters.isNotEmpty())) {
				PagesViewModel.State(details.filterChapters(branch), readingState, branch)
			} else {
				null
			}
		}.flowOn(Dispatchers.IO)
			.observe(this, viewModel::updateState)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPagesBinding {
		return FragmentPagesBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentPagesBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		spanResolver = GridSpanResolver(binding.root.resources)
		selectionController = ListSelectionController(
			appCompatDelegate = checkNotNull(findAppCompatDelegate()),
			decoration = PagesSelectionDecoration(binding.root.context),
			registryOwner = this,
			callback = this,
		)
		thumbnailsAdapter = PageThumbnailAdapter(
			clickListener = this@PagesFragment,
		)
		viewModel.gridScale.observe(viewLifecycleOwner, ::onGridScaleChanged) // before rv initialization
		with(binding.recyclerView) {
			addItemDecoration(TypedListSpacingDecoration(context, false))
			checkNotNull(selectionController).attachToRecyclerView(this)
			adapter = thumbnailsAdapter
			setHasFixedSize(true)
			PagerNestedScrollHelper(this).bind(viewLifecycleOwner)
			addOnLayoutChangeListener(spanResolver)
			addOnScrollListener(ScrollListener().also { scrollListener = it })
			(layoutManager as GridLayoutManager).let {
				it.spanSizeLookup = spanSizeLookup
				it.spanCount = checkNotNull(spanResolver).spanCount
			}
		}
		parentViewModel.emptyReason.observe(viewLifecycleOwner, ::onNoChaptersChanged)
		viewModel.thumbnails.observe(viewLifecycleOwner, ::onThumbnailsChanged)
		viewModel.onPageSaved.observeEvent(this, PagesSavedObserver(binding.recyclerView))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		combine(
			viewModel.isLoading,
			viewModel.thumbnails,
		) { loading, content ->
			loading && content.isEmpty()
		}.observe(viewLifecycleOwner) {
			binding.progressBar.showOrHide(it)
		}
		viewModel.isLoadingUp.observe(viewLifecycleOwner) { binding.progressBarTop.showOrHide(it) }
		viewModel.isLoadingDown.observe(viewLifecycleOwner) { binding.progressBarBottom.showOrHide(it) }
	}

	override fun onDestroyView() {
		spanResolver = null
		scrollListener = null
		thumbnailsAdapter = null
		selectionController = null
		spanSizeLookup.invalidateCache()
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeBask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeBask)
		viewBinding?.recyclerView?.setPadding(
			barsInsets.left,
			barsInsets.top,
			barsInsets.right,
			barsInsets.bottom,
		)
		return insets.consumeAll(typeBask)
	}

	override fun onItemClick(item: PageThumbnail, view: View) {
		if (selectionController?.onItemClick(item.page.id) == true) {
			return
		}
		val listener = findParentCallback(ReaderNavigationCallback::class.java)
		if (listener != null && listener.onPageSelected(item.page)) {
			dismissParentDialog()
		} else {
			router.openReader(
				ReaderIntent.Builder(view.context)
					.manga(parentViewModel.getMangaOrNull() ?: return)
					.state(ReaderState(item.page.chapterId, item.page.index, 0))
					.build(),
			)
		}
	}

	override fun onItemLongClick(item: PageThumbnail, view: View): Boolean {
		return selectionController?.onItemLongClick(view, item.page.id) == true
	}

	override fun onItemContextClick(item: PageThumbnail, view: View): Boolean {
		return selectionController?.onItemContextClick(view, item.page.id) == true
	}

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding?.recyclerView?.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu,
	): Boolean {
		menuInflater.inflate(R.menu.mode_pages, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_save -> {
				viewModel.savePages(pageSaveHelper, collectSelectedPages())
				mode?.finish()
				true
			}

			else -> false
		}
	}

	private suspend fun onThumbnailsChanged(list: List<ListModel>) {
		val adapter = thumbnailsAdapter ?: return
		if (adapter.itemCount == 0) {
			var position = list.indexOfFirst { it is PageThumbnail && it.isCurrent }
			if (position > 0) {
				val spanCount = spanResolver?.spanCount ?: 0
				val offset = if (position > spanCount + 1) {
					(resources.getDimensionPixelSize(R.dimen.manga_list_details_item_height) * 0.6).roundToInt()
				} else {
					position = 0
					0
				}
				val scrollCallback = RecyclerViewScrollCallback(requireViewBinding().recyclerView, position, offset)
				adapter.emit(list)
				scrollCallback.run()
			} else {
				adapter.emit(list)
			}
		} else {
			adapter.emit(list)
		}
		spanSizeLookup.invalidateCache()
		viewBinding?.recyclerView?.let {
			scrollListener?.postInvalidate(it)
		}
	}

	private fun onGridScaleChanged(scale: Float) {
		spanSizeLookup.invalidateCache()
		spanResolver?.setGridSize(scale, requireViewBinding().recyclerView)
	}

	private fun onNoChaptersChanged(reason: EmptyMangaReason?) {
		with(viewBinding ?: return) {
			textViewHolder.setTextAndVisible(reason?.msgResId ?: 0)
			recyclerView.isInvisible = reason != null
		}
	}

	private fun collectSelectedPages(): Set<ReaderPage> {
		val checkedIds = selectionController?.peekCheckedIds() ?: return emptySet()
		val items = thumbnailsAdapter?.items ?: return emptySet()
		val result = ArraySet<ReaderPage>(checkedIds.size)
		for (item in items) {
			if (item is PageThumbnail && item.page.id in checkedIds) {
				result.add(item.page)
			}
		}
		return result
	}

	private inner class ScrollListener : BoundsScrollListener(3, 3) {

		override fun onScrolledToStart(recyclerView: RecyclerView) {
			viewModel.loadPrevChapter()
		}

		override fun onScrolledToEnd(recyclerView: RecyclerView) {
			viewModel.loadNextChapter()
		}
	}

	private inner class SpanSizeLookup : GridLayoutManager.SpanSizeLookup() {

		init {
			isSpanIndexCacheEnabled = true
			isSpanGroupIndexCacheEnabled = true
		}

		override fun getSpanSize(position: Int): Int {
			val total = (viewBinding?.recyclerView?.layoutManager as? GridLayoutManager)?.spanCount ?: return 1
			return when (thumbnailsAdapter?.getItemViewType(position)) {
				ListItemType.PAGE_THUMB.ordinal -> 1
				else -> total
			}
		}

		fun invalidateCache() {
			invalidateSpanGroupIndexCache()
			invalidateSpanIndexCache()
		}
	}
}
