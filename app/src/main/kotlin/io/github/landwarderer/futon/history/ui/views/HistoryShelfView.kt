package io.github.landwarderer.futon.history.ui.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.getAnimationDuration
import io.github.landwarderer.futon.databinding.ViewHistoryShelfBinding
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import org.koitharu.kotatsu.parsers.model.Manga

private const val COVER_ASPECT = 13f / 18f
private const val MIN_SPINE_DP = 44
private const val SPINE_RANGE_DP = 30
private const val SPINE_GAP_DP = 3
private const val PULLED_OUT_GAP_DP = 10

class HistoryShelfView(
	context: Context,
	analyzer: SpineCropAnalyzer,
	scope: LifecycleCoroutineScope,
	private val onOpen: (Manga) -> Unit,
) : LinearLayout(context), HistoryModeView {

	private val binding = ViewHistoryShelfBinding.inflate(LayoutInflater.from(context), this)
	private val binder = SpineBinder(analyzer, scope)
	private val shelfAdapter = ShelfAdapter()
	private var entries: List<HistoryEntry> = emptyList()
	private var maxChapters = 1
	private var pulledOut: Long? = null

	init {
		orientation = VERTICAL
		with(binding.recyclerView) {
			layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
			adapter = shelfAdapter
			itemAnimator = null
		}
		binding.recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
			if (bottom - top != oldBottom - oldTop) {
				binding.recyclerView.post { shelfAdapter.refresh() }
			}
		}
		binding.layoutPanel.setOnClickListener {
			entries.firstOrNull { it.manga.id == pulledOut }?.let { onOpen(it.manga) }
		}
		updatePanel()
	}

	override fun setState(state: HistoryViewsState) {
		val entries = state.entries
		this.entries = entries
		maxChapters = entries.maxOfOrNull { it.chaptersCount }?.coerceAtLeast(1) ?: 1
		if (entries.none { it.manga.id == pulledOut }) {
			pulledOut = null
		}
		updatePanel()
		shelfAdapter.refresh()
	}

	override val scrollsVertically = false

	override fun setBottomInset(inset: Int) {
		updatePadding(bottom = inset)
	}

	private fun updatePanel() {
		val entry = entries.firstOrNull { it.manga.id == pulledOut }
		if (entry == null) {
			binding.textViewTitle.text = context.getString(R.string.history_shelf_count, entries.size)
			binding.textViewMeta.setText(R.string.history_tap_spine)
		} else {
			binding.textViewTitle.text = entry.manga.title
			val percent = (entry.percent.coerceIn(0f, 1f) * 100f).toInt()
			binding.textViewMeta.text = context.getString(
				R.string.history_shelf_meta,
				percent,
				resources.getQuantityString(R.plurals.chapters, entry.chaptersCount, entry.chaptersCount),
			)
		}
	}

	private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

	private inner class ShelfAdapter : RecyclerView.Adapter<SpineHolder>() {

		@SuppressLint("NotifyDataSetChanged")
		fun refresh() = notifyDataSetChanged()

		override fun getItemCount() = entries.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SpineHolder(SpineView(parent.context))

		override fun onBindViewHolder(holder: SpineHolder, position: Int) = holder.bind(entries[position])

		override fun onViewRecycled(holder: SpineHolder) = holder.recycle()
	}

	private inner class SpineHolder(private val spine: SpineView) : RecyclerView.ViewHolder(spine) {

		private var entry: HistoryEntry? = null
		private var analysis: Job? = null
		private var animator: ValueAnimator? = null

		private val bookHeight: Int
			get() = binding.recyclerView.run { measuredHeight - paddingTop - paddingBottom }.coerceAtLeast(1)
		private val coverWidth: Int
			get() = (bookHeight * COVER_ASPECT).toInt()

		init {
			spine.setOnClickListener {
				val current = entry ?: return@setOnClickListener
				if (pulledOut == current.manga.id) {
					onOpen(current.manga)
				} else {
					togglePulledOut(current)
				}
			}
			spine.setOnLongClickListener {
				val current = entry ?: return@setOnLongClickListener false
				if (pulledOut == current.manga.id) {
					togglePulledOut(current)
					true
				} else {
					false
				}
			}
		}

		fun bind(item: HistoryEntry) {
			analysis?.cancel()
			animator?.cancel()
			entry = item
			binder.bind(spine, item, coverWidth, bookHeight)
			applyGeometry(geometry(item, pulledOut == item.manga.id))
			analysis = binder.analyze(item.manga) {
				if (entry === item) {
					animateTo(geometry(item, pulledOut == item.manga.id))
				}
			}
		}

		fun recycle() {
			analysis?.cancel()
			animator?.cancel()
			entry = null
			spine.disposeImage()
		}

		private fun togglePulledOut(item: HistoryEntry) {
			val previous = pulledOut
			pulledOut = if (previous == item.manga.id) null else item.manga.id
			updatePanel()
			animateTo(geometry(item, pulledOut == item.manga.id))
			if (previous != null) {
				val previousIndex = entries.indexOfFirst { it.manga.id == previous }
				if (previousIndex >= 0 && previous != item.manga.id) {
					shelfAdapter.notifyItemChanged(previousIndex)
				}
			}
			if (pulledOut != null) {
				val list = binding.recyclerView
				list.postDelayed(
					{
						if (entry === item) {
							list.smoothScrollBy(spine.left - (list.width - coverWidth) / 2, 0)
						}
					},
					context.getAnimationDuration(R.integer.config_defaultAnimTime),
				)
			}
		}

		private fun geometry(item: HistoryEntry, isOut: Boolean): Geometry {
			if (isOut) {
				return Geometry(coverWidth, coverWidth.toFloat(), dp(PULLED_OUT_GAP_DP), 0f, true)
			}
			val width = dp(MIN_SPINE_DP) + dp(SPINE_RANGE_DP) * item.chaptersCount / maxChapters
			return Geometry(
				width = width,
				visible = width.toFloat(),
				margin = dp(SPINE_GAP_DP),
				offset = binder.cropOffset(item.manga, width.toFloat(), coverWidth.toFloat(), width.toFloat()),
				isOut = false,
			)
		}

		private fun applyGeometry(target: Geometry) {
			val params = (spine.layoutParams as? RecyclerView.LayoutParams)
				?: RecyclerView.LayoutParams(target.width, bookHeight)
			params.width = target.width
			params.height = bookHeight
			params.marginEnd = target.margin
			spine.layoutParams = params
			spine.visibleWidth = target.visible
			spine.cropOffset = target.offset
			spine.isPulledOut = target.isOut
			spine.translationZ = if (target.isOut) dp(6).toFloat() else 0f
		}

		private fun animateTo(target: Geometry) {
			animator?.cancel()
			val params = spine.layoutParams as? RecyclerView.LayoutParams ?: return applyGeometry(target)
			val startWidth = params.width
			val startMargin = params.marginEnd
			val startVisible = spine.visibleWidth
			val startOffset = spine.cropOffset
			spine.isPulledOut = target.isOut
			spine.translationZ = if (target.isOut) dp(6).toFloat() else 0f
			animator = ValueAnimator.ofFloat(0f, 1f).apply {
				duration = context.getAnimationDuration(R.integer.config_defaultAnimTime)
				interpolator = FastOutSlowInInterpolator()
				addUpdateListener {
					val t = it.animatedFraction
					params.width = lerp(startWidth, target.width, t)
					params.marginEnd = lerp(startMargin, target.margin, t)
					spine.layoutParams = params
					spine.visibleWidth = startVisible + (target.visible - startVisible) * t
					spine.cropOffset = startOffset + (target.offset - startOffset) * t
				}
				start()
			}
		}

		private fun lerp(from: Int, to: Int, t: Float) = from + ((to - from) * t).toInt()
	}

	private data class Geometry(
		val width: Int,
		val visible: Float,
		val margin: Int,
		val offset: Float,
		val isOut: Boolean,
	)
}
