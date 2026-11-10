package io.github.landwarderer.futon.history.ui.views

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ViewHistoryViewsBinding
import io.github.landwarderer.futon.history.domain.HistoryViewMode
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import io.github.landwarderer.futon.main.ui.dockClearance
import org.koitharu.kotatsu.parsers.model.Manga

/** Switches the history screen between the regular list and its other views. */
class HistoryViewsDelegate(
	private val binding: ViewHistoryViewsBinding,
	private val listView: View,
	private val viewModel: HistoryViewsViewModel,
	private val spineCropAnalyzer: SpineCropAnalyzer,
	private val lifecycleOwner: LifecycleOwner,
	private val onOpen: (Manga) -> Unit,
	private val onBarVisibilityChanged: (Boolean) -> Unit,
	private val appBarScrollRange: () -> Int,
	private val onFixedViewShown: () -> Unit,
) {

	private val context: Context
		get() = binding.root.context

	private val modeViews = HashMap<HistoryViewMode, View>()
	private var displayedMode: HistoryViewMode? = null
	private var systemInset = 0

	private val bottomInset: Int
		get() = systemInset + context.dockClearance(viewModel.isMinimalTheme.value)

	fun attach(isSearching: Flow<Boolean>) {
		binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyBottomInsets() }
		binding.chipGroupModes.setOnCheckedStateChangeListener { _, checkedIds ->
			val mode = chipModes.entries.firstOrNull { it.value == checkedIds.firstOrNull() }?.key
			if (mode != null && mode != viewModel.mode.value) {
				viewModel.setMode(mode)
			}
		}
		viewModel.isMinimalTheme.observe(lifecycleOwner) { applyBottomInsets() }
		viewModel.mode.observe(lifecycleOwner) { mode ->
			binding.chipGroupModes.check(checkNotNull(chipModes[mode]))
		}
		combine(viewModel.mode, isSearching) { mode, searching ->
			if (searching) HistoryViewMode.LIST to true else mode to false
		}.observe(lifecycleOwner) { (mode, searching) ->
			binding.scrollViewModes.isGone = searching
			onBarVisibilityChanged(!searching)
			show(mode)
		}
		// Only while visible, since reading elsewhere in the app updates statistics on every page.
		combine(viewModel.entries, viewModel.isStatsEnabled, ::Pair).observe(
			lifecycleOwner,
			Lifecycle.State.STARTED,
		) { (entries, isStatsEnabled) ->
			updateEntries(entries, isStatsEnabled)
		}
	}

	fun setBottomInset(systemInset: Int) {
		this.systemInset = systemInset
		applyBottomInsets()
	}

	private fun applyBottomInsets() {
		modeViews.values.forEach { applyBottomInset(it as HistoryModeView) }
	}

	private fun applyBottomInset(view: HistoryModeView) {
		// The screen is laid out taller than visible by however far the app bar can collapse.
		view.setBottomInset(if (view.scrollsVertically) bottomInset else bottomInset + appBarScrollRange())
	}

	private fun show(mode: HistoryViewMode) {
		if (mode == displayedMode) {
			return
		}
		displayedMode = mode
		val isList = mode == HistoryViewMode.LIST
		listView.isVisible = isList
		binding.containerMode.isGone = isList
		modeViews.values.forEach { it.isVisible = false }
		if (isList) {
			binding.textViewEmpty.isVisible = false
			return
		}
		val view = modeViews.getOrPut(mode) { createModeView(mode) }
		view.isVisible = true
		if (!(view as HistoryModeView).scrollsVertically) {
			onFixedViewShown()
		}
		updateEntries(viewModel.entries.value, viewModel.isStatsEnabled.value)
	}

	private fun createModeView(mode: HistoryViewMode): View {
		val view: View = when (mode) {
			HistoryViewMode.RECENT -> HistoryRecentView(context, onOpen)
			HistoryViewMode.CALENDAR -> HistoryCalendarView(context, onOpen)
			HistoryViewMode.SHELF -> HistoryShelfView(context, spineCropAnalyzer, lifecycleOwner.lifecycleScope, onOpen)
			HistoryViewMode.WHEEL -> HistoryWheelView(context, spineCropAnalyzer, lifecycleOwner.lifecycleScope, onOpen)
			HistoryViewMode.LIST -> error("The list is hosted by the fragment")
		}
		applyBottomInset(view as HistoryModeView)
		binding.containerMode.addView(
			view,
			ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
		)
		return view
	}

	private fun updateEntries(entries: List<HistoryEntry>, isStatsEnabled: Boolean) {
		val mode = displayedMode ?: return
		if (mode == HistoryViewMode.LIST) {
			return
		}
		binding.textViewEmpty.isVisible = entries.isEmpty()
		binding.containerMode.isVisible = entries.isNotEmpty()
		(modeViews[mode] as? HistoryModeView)?.setEntries(entries, isStatsEnabled)
	}

	private companion object {

		val chipModes = mapOf(
			HistoryViewMode.LIST to R.id.chip_mode_list,
			HistoryViewMode.RECENT to R.id.chip_mode_recent,
			HistoryViewMode.CALENDAR to R.id.chip_mode_calendar,
			HistoryViewMode.SHELF to R.id.chip_mode_shelf,
			HistoryViewMode.WHEEL to R.id.chip_mode_wheel,
		)
	}
}
