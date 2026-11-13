package io.github.landwarderer.futon.history.ui.views

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.history.domain.SPINE_COLUMNS
import io.github.landwarderer.futon.history.domain.SpineCropAnalyzer
import io.github.landwarderer.futon.history.domain.bestSpineWindow
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import io.github.landwarderer.futon.history.domain.spineWindowColumns
import org.koitharu.kotatsu.parsers.model.Manga

/** Loads covers into spines and picks the most detailed strip of each cover once it has been analysed. */
class SpineBinder(
	private val analyzer: SpineCropAnalyzer,
	private val scope: LifecycleCoroutineScope,
) {

	fun bind(spine: SpineView, entry: HistoryEntry, coverWidth: Int, coverHeight: Int) {
		spine.percent = entry.percent
		spine.contentDescription = entry.manga.title
		spine.coverHeight = coverHeight.toFloat()
		spine.coverWidth = coverWidth.toFloat()
		spine.setCover(entry.manga, coverWidth, coverHeight)
	}

	/** Starts analysing the cover unless that is already done, and calls [onReady] once it is. */
	fun analyze(manga: Manga, onReady: () -> Unit): Job? {
		if (analyzer.peekEnergy(manga) != null) {
			return null
		}
		return scope.launch {
			analyzer.getEnergy(manga) ?: return@launch
			onReady()
		}
	}

	/** How far into the cover a spine [width] wide starts, so the [visible] part shows its most detailed strip. */
	fun cropOffset(manga: Manga, visible: Float, coverWidth: Float, width: Float): Float {
		val energy = analyzer.peekEnergy(manga)
		val windowColumns = spineWindowColumns(visible, coverWidth)
		val start = if (energy != null) {
			bestSpineWindow(energy, windowColumns)
		} else {
			(SPINE_COLUMNS - windowColumns) / 2
		}
		return (start.toFloat() / SPINE_COLUMNS * coverWidth).coerceIn(0f, (coverWidth - width).coerceAtLeast(0f))
	}
}
