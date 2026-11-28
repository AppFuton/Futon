package io.github.landwarderer.futon.tracker.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val MOSAIC_MAX_CELLS = 9
private const val COVER_ASPECT = 13f / 18f
private const val MILLIS_PER_DAY = 86_400_000L

data class TreemapRect(
	val x: Float,
	val y: Float,
	val width: Float,
	val height: Float,
)

/** Squarified treemap layout. Items with no weight are left out. */
fun <T> squarify(
	items: List<T>,
	x: Float,
	y: Float,
	width: Float,
	height: Float,
	weight: (T) -> Double,
): List<Pair<T, TreemapRect>> {
	class Node(val item: T, val area: Double)

	val weighted = items.filter { weight(it) > 0.0 }
	val total = weighted.sumOf(weight)
	if (total <= 0.0 || width <= 0f || height <= 0f) {
		return emptyList()
	}
	val scale = width.toDouble() * height / total
	val nodes = weighted.map { Node(it, weight(it) * scale) }.sortedByDescending { it.area }
	val result = ArrayList<Pair<T, TreemapRect>>(nodes.size)
	var left = x.toDouble()
	var top = y.toDouble()
	var remainingWidth = width.toDouble()
	var remainingHeight = height.toDouble()
	var row = ArrayList<Node>()

	fun worst(candidate: List<Node>, side: Double): Double {
		val sum = candidate.sumOf { it.area }
		val largest = candidate.maxOf { it.area }
		val smallest = candidate.minOf { it.area }
		return max(side * side * largest / (sum * sum), sum * sum / (side * side * smallest))
	}

	fun place(current: List<Node>) {
		val sum = current.sumOf { it.area }
		if (remainingWidth >= remainingHeight) {
			val columnWidth = sum / remainingHeight
			var cursor = top
			for (node in current) {
				val nodeHeight = node.area / columnWidth
				result += node.item to TreemapRect(
					left.toFloat(),
					cursor.toFloat(),
					columnWidth.toFloat(),
					nodeHeight.toFloat(),
				)
				cursor += nodeHeight
			}
			left += columnWidth
			remainingWidth -= columnWidth
		} else {
			val rowHeight = sum / remainingWidth
			var cursor = left
			for (node in current) {
				val nodeWidth = node.area / rowHeight
				result += node.item to TreemapRect(
					cursor.toFloat(),
					top.toFloat(),
					nodeWidth.toFloat(),
					rowHeight.toFloat(),
				)
				cursor += nodeWidth
			}
			top += rowHeight
			remainingHeight -= rowHeight
		}
	}

	for (node in nodes) {
		val side = min(remainingWidth, remainingHeight)
		if (row.isEmpty() || worst(row + node, side) <= worst(row, side)) {
			row.add(node)
		} else {
			place(row)
			row = arrayListOf(node)
		}
	}
	if (row.isNotEmpty()) {
		place(row)
	}
	return result
}

/** How many of the heaviest items get their own tile; the others fold into a single "+N" tile. */
data class TileFold(
	val kept: Int,
	val isEqualSized: Boolean,
)

/**
 * Decides which tiles of [sortedWeights], heaviest first, are too small to show in [area].
 * A zoomed-in level must always keep at least one tile, or tapping its "+N" would show the same "+N" forever,
 * so when nothing is big enough on its own it lays out as many equal tiles as fit instead.
 */
fun foldTiles(sortedWeights: List<Double>, area: Float, minArea: Float, isZoomed: Boolean): TileFold {
	val count = sortedWeights.size
	val total = sortedWeights.sum()
	if (total <= 0.0 || area <= 0f) {
		return TileFold(count, false)
	}
	val kept = sortedWeights.count { it / total * area >= minArea }
	if (count - kept < 2) {
		return TileFold(count, false)
	}
	if (kept > 0 || !isZoomed) {
		return TileFold(kept, false)
	}
	val capacity = (area / minArea).toInt().coerceAtLeast(2)
	return TileFold(capacity - 1, true)
}

data class MosaicGrid(
	val columns: Int,
	val rows: Int,
)

/** Picks a full grid of at most [count] covers whose cells come closest to the shape of a cover. */
fun mosaicGrid(count: Int, width: Float, height: Float): MosaicGrid {
	val limit = count.coerceIn(1, MOSAIC_MAX_CELLS)
	var best = MosaicGrid(1, 1)
	var bestScore = Float.NEGATIVE_INFINITY
	for (columns in 1..limit) {
		for (rows in 1..limit / columns) {
			val cellAspect = (width / columns) / (height / rows)
			val fit = min(cellAspect, COVER_ASPECT) / max(cellAspect, COVER_ASPECT)
			val score = fit * sqrt((columns * rows).toFloat())
			if (score > bestScore) {
				bestScore = score
				best = MosaicGrid(columns, rows)
			}
		}
	}
	return best
}

/** When the shelf's window opens, or null if the tracker has not run yet and so nothing can be in it. */
fun updatesShelfStart(window: UpdatesShelfWindow, lastRunAt: Long, now: Long): Long? = when (window) {
	UpdatesShelfWindow.LAST_CHECK -> lastRunAt.takeIf { it > 0L }
	UpdatesShelfWindow.LAST_24_HOURS -> now - MILLIS_PER_DAY
}
