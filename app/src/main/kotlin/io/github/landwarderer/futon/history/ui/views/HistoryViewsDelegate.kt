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
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ViewHistoryViewsBinding
import io.github.landwarderer.futon.history.domain.HistoryViewMode
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.main.ui.dockClearance
import org.koitharu.kotatsu.parsers.model.Manga

/** Shows the history screen as the regular list or one of its other views, as picked in settings. */
class HistoryViewsDelegate(
	private val binding: ViewHistoryViewsBinding,
	private val listView: View,
	private val viewModel: HistoryViewsViewModel,
	private val spineCropAnalyzer: SpineCropAnalyzer,
	private val lifecycleOwner: LifecycleOwner,
	private val onOpen: (Manga) -> Unit,
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
		viewModel.isMinimalTheme.observe(lifecycleOwner) { applyBottomInsets() }
		viewModel.wheelStyle.observe(lifecycleOwner) { style ->
			(modeViews[HistoryViewMode.WHEEL] as? HistoryWheelView)?.style = style
		}
		// Searching filters the list, so the list shows while a search is open.
		combine(viewModel.mode, isSearching) { mode, searching ->
			if (searching) HistoryViewMode.LIST else mode
		}.observe(lifecycleOwner, ::show)
		// Only while visible, since reading elsewhere in the app updates statistics on every page.
		viewModel.state.observe(lifecycleOwner, Lifecycle.State.STARTED, ::updateState)
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
		updateState(viewModel.state.value)
	}

	private fun createModeView(mode: HistoryViewMode): View {
		val view: View = when (mode) {
			HistoryViewMode.RECENT -> HistoryRecentView(context, onOpen)
			HistoryViewMode.CALENDAR -> HistoryCalendarView(context, onOpen)
			HistoryViewMode.SHELF -> HistoryShelfView(context, spineCropAnalyzer, lifecycleOwner.lifecycleScope, onOpen)
			HistoryViewMode.WHEEL -> HistoryWheelView(context, spineCropAnalyzer, lifecycleOwner.lifecycleScope, onOpen).also {
				it.style = viewModel.wheelStyle.value
			}
			HistoryViewMode.LIST -> error("The list is hosted by the fragment")
		}
		applyBottomInset(view as HistoryModeView)
		binding.containerMode.addView(
			view,
			ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
		)
		return view
	}

	private fun updateState(state: HistoryViewsState?) {
		val mode = displayedMode ?: return
		if (mode == HistoryViewMode.LIST) {
			return
		}
		if (state == null) {
			// Still loading: show neither the empty text nor an empty view.
			binding.textViewEmpty.isVisible = false
			binding.containerMode.isVisible = false
			return
		}
		binding.textViewEmpty.isVisible = state.entries.isEmpty()
		binding.containerMode.isVisible = state.entries.isNotEmpty()
		(modeViews[mode] as? HistoryModeView)?.setState(state)
	}
}
