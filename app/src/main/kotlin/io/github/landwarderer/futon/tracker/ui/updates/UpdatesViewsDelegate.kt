package io.github.landwarderer.futon.tracker.ui.updates

import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ViewUpdatesViewsBinding
import io.github.landwarderer.futon.main.ui.dockClearance
import io.github.landwarderer.futon.main.ui.owners.AppBarOwner
import io.github.landwarderer.futon.main.ui.owners.BottomNavOwner
import io.github.landwarderer.futon.tracker.domain.UpdatesShelf
import io.github.landwarderer.futon.tracker.domain.UpdatesViewMode
import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import org.koitharu.kotatsu.parsers.model.Manga

/** Switches a screen of updates between its regular list and the map. */
class UpdatesViewsDelegate(
	private val binding: ViewUpdatesViewsBinding,
	private val listView: View,
	private val activity: FragmentActivity,
	private val lifecycleOwner: LifecycleOwner,
	private val mode: StateFlow<UpdatesViewMode>,
	private val entries: StateFlow<List<MangaTracking>?>,
	private val shelf: StateFlow<UpdatesShelf?>,
	private val isMinimalTheme: () -> Boolean,
	private val onModeSelected: (UpdatesViewMode) -> Unit,
	private val onOpen: (Manga) -> Unit,
) {

	private var mapView: UpdatesMapView? = null
	private var systemInset = 0

	private val zoomOutCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			mapView?.zoomOut()
		}
	}

	fun attach() {
		activity.onBackPressedDispatcher.addCallback(lifecycleOwner, zoomOutCallback)
		binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyBottomInset() }
		binding.chipGroupModes.setOnCheckedStateChangeListener { _, checkedIds ->
			val selected = if (checkedIds.firstOrNull() == R.id.chip_mode_list) {
				UpdatesViewMode.LIST
			} else {
				UpdatesViewMode.MAP
			}
			if (selected != mode.value) {
				onModeSelected(selected)
			}
		}
		mode.observe(lifecycleOwner, ::show)
		entries.observe(lifecycleOwner) { list ->
			if (list != null) {
				mapView?.setEntries(list)
			}
		}
		shelf.observe(lifecycleOwner) { value ->
			if (value != null) {
				mapView?.setShelf(value)
			}
		}
	}

	fun setSystemInset(inset: Int) {
		systemInset = inset
		applyBottomInset()
	}

	private fun show(mode: UpdatesViewMode) {
		val isMap = mode == UpdatesViewMode.MAP
		binding.chipGroupModes.check(if (isMap) R.id.chip_mode_map else R.id.chip_mode_list)
		listView.isGone = isMap
		binding.containerMap.isVisible = isMap
		updateZoomCallback()
		if (isMap) {
			ensureMapView()
			// The map does not scroll, so it could not otherwise bring the app bar or the dock back.
			(activity as? AppBarOwner)?.appBar?.setExpanded(true, true)
			(activity as? BottomNavOwner)?.bottomNav?.animate()?.translationY(0f)
		}
	}

	private fun ensureMapView() {
		if (mapView != null) {
			return
		}
		val view = UpdatesMapView(
			context = binding.root.context,
			onOpen = onOpen,
			onZoomChanged = { updateZoomCallback() },
		)
		mapView = view
		binding.containerMap.addView(
			view,
			ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
		)
		applyBottomInset()
		entries.value?.let(view::setEntries)
		shelf.value?.let(view::setShelf)
	}

	private fun updateZoomCallback() {
		zoomOutCallback.isEnabled = binding.containerMap.isVisible && mapView?.isZoomed == true
	}

	private fun applyBottomInset() {
		val view = mapView ?: return
		val dock = if ((activity as? BottomNavOwner)?.bottomNav != null) {
			view.context.dockClearance(isMinimalTheme())
		} else {
			0
		}
		// The screen is laid out taller than visible by however far the app bar can collapse.
		view.setBottomInset(systemInset + dock + ((activity as? AppBarOwner)?.appBar?.totalScrollRange ?: 0))
	}
}
