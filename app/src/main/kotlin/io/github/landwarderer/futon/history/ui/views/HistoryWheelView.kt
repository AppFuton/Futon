package io.github.landwarderer.futon.history.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleCoroutineScope
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ViewHistoryWheelBinding
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import org.koitharu.kotatsu.parsers.model.Manga

/** Recent manga on a wheel: a flat shelf turned by a click wheel, or spines fanned around a rim. */
class HistoryWheelView(
	context: Context,
	analyzer: SpineCropAnalyzer,
	scope: LifecycleCoroutineScope,
	private val onOpen: (Manga) -> Unit,
) : LinearLayout(context), HistoryModeView, SpineArcView.Listener, ClickWheelView.Listener {

	private val binding = ViewHistoryWheelBinding.inflate(LayoutInflater.from(context), this)
	private val spines = SpineArcView(context, SpineBinder(analyzer, scope))
	private var entries: List<HistoryEntry> = emptyList()
	private var selectedId: Long? = null

	init {
		orientation = VERTICAL
		binding.containerSpines.addView(
			spines,
			FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
		)
		spines.listener = this
		binding.wheel.listener = this
		binding.chipGroupStyle.setOnCheckedStateChangeListener { _, checkedIds ->
			applyStyle(isClickWheel = checkedIds.firstOrNull() != R.id.chip_arc)
		}
		binding.layoutPanel.setOnClickListener { openSelected() }
		applyStyle(isClickWheel = true)
	}

	override val scrollsVertically = false

	override fun setEntries(entries: List<HistoryEntry>, isStatsEnabled: Boolean) {
		this.entries = entries
		val index = entries.indexOfFirst { it.manga.id == selectedId }.coerceAtLeast(0)
		selectedId = entries.getOrNull(index)?.manga?.id
		spines.setEntries(entries, index)
		binding.wheel.isPeeking = spines.isSelectedPulledOut
		updatePanel()
	}

	override fun setBottomInset(inset: Int) {
		updatePadding(bottom = inset)
	}

	override fun onSpineSelected(index: Int) {
		selectedId = entries.getOrNull(index)?.manga?.id
		updatePanel()
	}

	override fun onSpinePulledOut(isPulledOut: Boolean) {
		binding.wheel.isPeeking = isPulledOut
	}

	override fun onSpineOpen(index: Int) {
		entries.getOrNull(index)?.let { onOpen(it.manga) }
	}

	override fun onWheelStep(delta: Int) = spines.step(delta)

	override fun onWheelOpen() = openSelected()

	override fun onWheelLatest() = spines.select(0)

	override fun onWheelPeek() = spines.setPulledOut(!spines.isSelectedPulledOut)

	private fun applyStyle(isClickWheel: Boolean) {
		binding.wheel.isVisible = isClickWheel
		spines.autoPullOut = !isClickWheel
		spines.isCurved = !isClickWheel
		spines.setPulledOut(!isClickWheel, animate = false)
	}

	private fun openSelected() {
		entries.firstOrNull { it.manga.id == selectedId }?.let { onOpen(it.manga) }
	}

	private fun updatePanel() {
		val entry = entries.firstOrNull { it.manga.id == selectedId }
		if (entry == null) {
			binding.textViewTitle.text = null
			binding.textViewMeta.text = null
			return
		}
		binding.textViewTitle.text = entry.manga.title
		val percent = (entry.percent.coerceIn(0f, 1f) * 100f).toInt()
		binding.textViewMeta.text = context.getString(
			R.string.history_shelf_meta,
			percent,
			resources.getQuantityString(R.plurals.chapters, entry.chaptersCount, entry.chaptersCount),
		)
	}
}
