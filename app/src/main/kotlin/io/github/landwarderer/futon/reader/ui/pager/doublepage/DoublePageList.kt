package io.github.landwarderer.futon.reader.ui.pager.doublepage

import io.github.landwarderer.futon.reader.ui.pager.ReaderPage

/**
 * Items of the double-page reader adapter: the reader pages padded with blank spacers so that
 * two chapters never share a spread (each chapter takes an even number of slots), optionally
 * showing the first page of every chapter alone, as a cover.
 *
 * Adapter positions refer to [items]; original positions refer to the unpadded page list
 * handed to the reader (always in natural order, even when [items] are reversed).
 * Spreads are the adapter position pairs (2k, 2k + 1).
 */
class DoublePageList private constructor(
	val items: List<ReaderPage>,
	private val toOriginal: IntArray,
	private val toAdapter: IntArray,
) {

	val size: Int
		get() = items.size

	/** Original position of the page at [adapterPosition], or -1 for a spacer or out of bounds. */
	fun toOriginalPosition(adapterPosition: Int): Int = toOriginal.getOrElse(adapterPosition) { -1 }

	/** Adapter position of the page at [originalPosition], or -1 if out of bounds. */
	fun toAdapterPosition(originalPosition: Int): Int = toAdapter.getOrElse(originalPosition) { -1 }

	/**
	 * Original positions range covered by the adapter positions [firstPosition]..[lastPosition],
	 * spacers excluded, or null if there are no real pages in it.
	 */
	fun toOriginalRange(firstPosition: Int, lastPosition: Int): IntRange? {
		if (firstPosition < 0 || lastPosition < firstPosition) {
			return null
		}
		var lower = Int.MAX_VALUE
		var upper = -1
		for (i in firstPosition..minOf(lastPosition, toOriginal.lastIndex)) {
			val pos = toOriginal[i]
			if (pos >= 0) {
				if (pos < lower) lower = pos
				if (pos > upper) upper = pos
			}
		}
		return if (upper < 0) null else lower..upper
	}

	/** First real page of the spread containing [adapterPosition], or null. */
	fun getSpreadPage(adapterPosition: Int): ReaderPage? {
		if (adapterPosition < 0) {
			return null
		}
		val start = adapterPosition and 1.inv()
		return items.getOrNull(start)?.takeUnless { it.isSpacer }
			?: items.getOrNull(start + 1)?.takeUnless { it.isSpacer }
	}

	companion object {

		val EMPTY = DoublePageList(emptyList(), IntArray(0), IntArray(0))

		fun build(pages: List<ReaderPage>, coverPage: Boolean, reversed: Boolean): DoublePageList {
			if (pages.isEmpty()) {
				return EMPTY
			}
			val items = ArrayList<ReaderPage>(pages.size + pages.size / 8 + 2)
			val toOriginal = ArrayList<Int>(pages.size + pages.size / 8 + 2)
			val toAdapter = IntArray(pages.size)
			var chapterStart = 0
			pages.forEachIndexed { i, page ->
				val isNewChapter = i == 0 || page.chapterId != pages[i - 1].chapterId
				if (isNewChapter) {
					if (i != 0) {
						padChapter(items, toOriginal, chapterStart, pages[i - 1])
					}
					chapterStart = items.size
					if (coverPage) {
						items += spacer(page, SPACER_LEADING)
						toOriginal += -1
					}
				}
				toAdapter[i] = items.size
				items += page
				toOriginal += i
			}
			padChapter(items, toOriginal, chapterStart, pages.last())
			if (reversed) {
				items.reverse()
				toOriginal.reverse()
				val last = items.lastIndex
				for (i in toAdapter.indices) {
					toAdapter[i] = last - toAdapter[i]
				}
			}
			return DoublePageList(items, toOriginal.toIntArray(), toAdapter)
		}

		private fun padChapter(
			items: MutableList<ReaderPage>,
			toOriginal: MutableList<Int>,
			chapterStart: Int,
			lastPage: ReaderPage,
		) {
			if ((items.size - chapterStart) % 2 != 0) {
				items += spacer(lastPage, SPACER_TRAILING)
				toOriginal += -1
			}
		}

		private fun spacer(page: ReaderPage, kind: Long) = ReaderPage(
			id = kind,
			url = "",
			preview = null,
			chapterId = page.chapterId,
			index = SPACER_INDEX,
			source = page.source,
		)

		private const val SPACER_INDEX = -1
		private const val SPACER_LEADING = Long.MIN_VALUE
		private const val SPACER_TRAILING = Long.MIN_VALUE + 1
	}
}

val ReaderPage.isSpacer: Boolean
	get() = index < 0
