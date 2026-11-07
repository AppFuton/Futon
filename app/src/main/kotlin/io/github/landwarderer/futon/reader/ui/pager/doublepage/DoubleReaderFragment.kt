package io.github.landwarderer.futon.reader.ui.pager.doublepage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.MainThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsFlow
import io.github.landwarderer.futon.core.ui.list.lifecycle.RecyclerViewLifecycleDispatcher
import io.github.landwarderer.futon.core.util.ext.firstVisibleItemPosition
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.FragmentReaderDoubleBinding
import io.github.landwarderer.futon.reader.domain.PageLoader
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.pager.BaseReaderFragment
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import io.github.landwarderer.futon.reader.ui.pager.visibleAdapterPositions
import javax.inject.Inject
import kotlin.math.absoluteValue

@AndroidEntryPoint
open class DoubleReaderFragment : BaseReaderFragment<FragmentReaderDoubleBinding>() {

	@Inject
	lateinit var networkState: NetworkState

	@Inject
	lateinit var pageLoader: PageLoader

	@Inject
	lateinit var settings: AppSettings

	private var recyclerLifecycleDispatcher: RecyclerViewLifecycleDispatcher? = null

	/** Adapter items (with spacers) and their mapping to the reader page positions */
	private var pageList = DoublePageList.EMPTY
	private var sourcePages: List<ReaderPage> = emptyList()
	private val pagesMutex = Mutex()

	protected open val isReversed: Boolean
		get() = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentReaderDoubleBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(
		binding: FragmentReaderDoubleBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		with(binding.recyclerView) {
			clipChildren = false
			clipToPadding = false
			adapter = readerAdapter
			recyclerLifecycleDispatcher = RecyclerViewLifecycleDispatcher().also {
				addOnScrollListener(it)
			}
			addOnScrollListener(PageScrollListener())
			DoublePageSnapHelper(settings).attachToRecyclerView(this)
		}
		settings.observeAsFlow(AppSettings.KEY_READER_DOUBLE_COVER_PAGE) { isReaderDoubleCoverPage }
			.drop(1)
			.observe(viewLifecycleOwner) {
				val pages = sourcePages
				if (pages.isNotEmpty()) {
					onPagesChanged(pages, getCurrentState())
				}
			}
	}

	override fun onDestroyView() {
		recyclerLifecycleDispatcher = null
		requireViewBinding().recyclerView.adapter = null
		super.onDestroyView()
	}

	override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) = pagesMutex.withLock {
		coroutineScope {
			sourcePages = pages
			val newList = DoublePageList.build(
				pages = pages,
				coverPage = settings.isReaderDoubleCoverPage,
				reversed = isReversed,
			)
			val items = launch {
				requireAdapter().setItems(newList.items)
				pageList = newList
				yield()
				viewBinding?.recyclerView?.let { rv ->
					recyclerLifecycleDispatcher?.invalidate(rv)
				}
			}
			if (pendingState != null) {
				var position = newList.items.indexOfFirst {
					it.chapterId == pendingState.chapterId && it.index == pendingState.page
				}
				items.join()
				if (position != -1) {
					position = position.toPagePosition()
					requireViewBinding().recyclerView.firstVisibleItemPosition = position
					notifyPageChanged(position, position + 1)
				} else {
					Snackbar.make(requireView(), R.string.not_found_404, Snackbar.LENGTH_SHORT)
						.show()
				}
			} else {
				items.join()
			}
		}
	}

	override fun onCreateAdapter() = DoublePagesAdapter(
		lifecycleOwner = viewLifecycleOwner,
		loader = pageLoader,
		readerSettingsProducer = viewModel.readerSettingsProducer,
		networkState = networkState,
		exceptionResolver = exceptionResolver,
	)

	override fun onZoomIn() {
		viewBinding?.frame?.onZoomIn()
	}

	override fun onZoomOut() {
		viewBinding?.frame?.onZoomOut()
	}

	override fun switchPageBy(delta: Int) {
		if (delta.absoluteValue > 1 || !isAnimationEnabled()) {
			scrollToAdapterPosition(getCurrentItem() + delta + delta)
			return
		}
		val rv = viewBinding?.recyclerView ?: return
		val distance = rv.width * delta
		rv.smoothScrollBy(distance, 0, AccelerateDecelerateInterpolator())
	}

	override fun switchPageTo(position: Int, smooth: Boolean) {
		val adapterPosition = pageList.toAdapterPosition(position)
		if (adapterPosition >= 0) {
			scrollToAdapterPosition(adapterPosition)
		}
	}

	override fun getCurrentState(): ReaderState? = viewBinding?.run {
		val page = pageList.getSpreadPage(getCurrentItem()) ?: return@run null
		ReaderState(
			chapterId = page.chapterId,
			page = page.index,
			scroll = 0,
		)
	}

	/**
	 * Resolves the positions against the adapter's own items, like [notifyVisiblePagesChanged], rather than
	 * against [pageList] or [sourcePages], which may not be the list the adapter shows yet. Spacers are not
	 * pages the view model knows, so the real pages at both ends of the visible range are reported.
	 */
	@MainThread
	private fun notifyPageChanged(lowerPos: Int, upperPos: Int) {
		val adapter = readerAdapter ?: return
		val positions = visibleAdapterPositions(lowerPos, upperPos, adapter.itemCount) ?: return
		val pages = positions.mapNotNull { pos -> adapter.getItemOrNull(pos)?.takeUnless { it.isSpacer } }
		viewModel.onCurrentPageChanged(pages.firstOrNull(), pages.lastOrNull())
	}

	private fun scrollToAdapterPosition(position: Int) {
		val lm = viewBinding?.recyclerView?.layoutManager as? LinearLayoutManager ?: return
		lm.scrollToPositionWithOffset(position.toPagePosition(), 0)
	}

	private fun getCurrentItem() = (requireViewBinding().recyclerView.layoutManager as LinearLayoutManager)
		.findFirstCompletelyVisibleItemPosition().toPagePosition()

	private fun Int.toPagePosition() = this and 1.inv()

	private inner class PageScrollListener : RecyclerView.OnScrollListener() {

		private var firstPos = RecyclerView.NO_POSITION
		private var lastPos = RecyclerView.NO_POSITION

		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			super.onScrolled(recyclerView, dx, dy)
			val lm = recyclerView.layoutManager as? LinearLayoutManager
			if (lm == null) {
				firstPos = RecyclerView.NO_POSITION
				lastPos = RecyclerView.NO_POSITION
				return
			}
			val newFirstPos = lm.findFirstVisibleItemPosition()
			val newLastPos = lm.findLastVisibleItemPosition()
			if (newFirstPos != firstPos || newLastPos != lastPos) {
				firstPos = newFirstPos
				lastPos = newLastPos
				notifyPageChanged(newFirstPos, newLastPos)
			}
		}
	}
}
