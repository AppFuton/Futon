package io.github.landwarderer.futon.reader.ui.pager

/**
 * Adapter positions to report for the visible range, or null when there is nothing to report.
 *
 * A negative position is NO_POSITION (nothing is laid out); clamping it to 0 would report the first loaded page.
 * Only the upper bound is clamped: a double-page spread can end one past the last page.
 */
internal fun visibleAdapterPositions(lowerPos: Int, upperPos: Int, itemCount: Int): IntRange? {
	if (lowerPos < 0 || upperPos < 0 || itemCount <= 0) {
		return null
	}
	val lastIndex = itemCount - 1
	return lowerPos.coerceAtMost(lastIndex)..upperPos.coerceAtMost(lastIndex)
}

/**
 * Indices of [lowerPage] and [upperPage] in this list, matched by chapter and page index rather than by position,
 * or null if either page is no longer loaded. Reversed modes report the pages back to front, so the result is
 * always ascending.
 */
internal fun List<ReaderPage>.visibleRangeOf(lowerPage: ReaderPage, upperPage: ReaderPage): IntRange? {
	val lowerIndex = indexOfPage(lowerPage)
	val upperIndex = indexOfPage(upperPage)
	if (lowerIndex < 0 || upperIndex < 0) {
		return null
	}
	return minOf(lowerIndex, upperIndex)..maxOf(lowerIndex, upperIndex)
}

private fun List<ReaderPage>.indexOfPage(page: ReaderPage): Int = indexOfFirst {
	it.chapterId == page.chapterId && it.index == page.index
}
