package io.github.landwarderer.futon.reader.ui.pager.doublepage

import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoublePageListTest {

	@Test
	fun `empty list`() {
		val list = DoublePageList.build(emptyList(), coverPage = true, reversed = true)
		assertEquals(0, list.size)
		assertEquals(-1, list.toAdapterPosition(0))
		assertNull(list.toOriginalRange(0, 1))
		assertNull(list.getSpreadPage(0))
	}

	@Test
	fun `single chapter even count has no spacers`() {
		val pages = chapters(4)
		val list = DoublePageList.build(pages, coverPage = false, reversed = false)
		assertEquals(pages, list.items)
		assertLayout(list, "1:0 1:1 | 1:2 1:3")
	}

	@Test
	fun `odd chapters are padded so that chapters never share a spread`() {
		val list = DoublePageList.build(chapters(3, 1, 2), coverPage = false, reversed = false)
		assertLayout(list, "1:0 1:1 | 1:2 _ | 2:0 _ | 3:0 3:1")
	}

	@Test
	fun `cover page is shown alone`() {
		val list = DoublePageList.build(chapters(3, 2), coverPage = true, reversed = false)
		assertLayout(list, "_ 1:0 | 1:1 1:2 | _ 2:0 | 2:1 _")
	}

	@Test
	fun `reversed keeps spreads and reading order`() {
		val list = DoublePageList.build(chapters(3, 1), coverPage = false, reversed = true)
		assertLayout(list, "_ 2:0 | _ 1:2 | 1:1 1:0")
		val coverList = DoublePageList.build(chapters(3, 1), coverPage = true, reversed = true)
		assertLayout(coverList, "2:0 _ | 1:2 1:1 | 1:0 _")
	}

	@Test
	fun `positions round trip`() {
		for (reversed in listOf(false, true)) {
			for (cover in listOf(false, true)) {
				val pages = chapters(5, 1, 4, 2)
				val list = DoublePageList.build(pages, cover, reversed)
				assertEquals(0, list.size % 2)
				val adapterPositions = pages.indices.map { list.toAdapterPosition(it) }
				assertEquals(pages.size, adapterPositions.toSet().size)
				pages.indices.forEach { i ->
					val pos = adapterPositions[i]
					assertEquals(pages[i], list.items[pos])
					assertEquals(i, list.toOriginalPosition(pos))
				}
				list.items.indices.forEach { pos ->
					val orig = list.toOriginalPosition(pos)
					if (orig < 0) {
						assertTrue(list.items[pos].isSpacer)
					} else {
						assertEquals(pos, list.toAdapterPosition(orig))
					}
				}
				// spreads never mix chapters
				for (spread in list.items.chunked(2)) {
					assertEquals(1, spread.map { it.chapterId }.toSet().size)
					assertTrue(spread.any { !it.isSpacer })
				}
			}
		}
	}

	@Test
	fun `visible range is mapped to original positions`() {
		val pages = chapters(3, 2)
		val list = DoublePageList.build(pages, coverPage = true, reversed = false)
		// _ 1:0 | 1:1 1:2 | _ 2:0 | 2:1 _
		assertEquals(0..0, list.toOriginalRange(0, 1))
		assertEquals(1..2, list.toOriginalRange(2, 3))
		assertEquals(2..3, list.toOriginalRange(3, 5))
		assertEquals(4..4, list.toOriginalRange(6, 8))
		assertNull(list.toOriginalRange(7, 7))
		val reversed = DoublePageList.build(pages, coverPage = true, reversed = true)
		// _ 2:1 | 2:0 _ | 1:2 1:1 | 1:0 _
		assertEquals(4..4, reversed.toOriginalRange(0, 1))
		assertEquals(1..2, reversed.toOriginalRange(4, 5))
		assertEquals(0..0, reversed.toOriginalRange(6, 7))
	}

	@Test
	fun `spread page skips spacers`() {
		val list = DoublePageList.build(chapters(3, 2), coverPage = true, reversed = false)
		assertEquals(page(1, 0), list.getSpreadPage(0))
		assertEquals(page(1, 0), list.getSpreadPage(1))
		assertEquals(page(1, 1), list.getSpreadPage(3))
		assertEquals(page(2, 1), list.getSpreadPage(6))
		assertNull(list.getSpreadPage(8))
		assertNull(list.getSpreadPage(-2))
	}

	@Test
	fun `spacers are stable across rebuilds`() {
		val a = DoublePageList.build(chapters(3), coverPage = true, reversed = false)
		val b = DoublePageList.build(chapters(1, 3), coverPage = true, reversed = false)
		val spacersA = a.items.filter { it.isSpacer }
		val spacersB = b.items.filter { it.isSpacer && it.chapterId == 2L }
		assertEquals(spacersA.map { it.id }, spacersB.map { it.id })
		assertEquals(b.items.size, b.items.map { it.id to it.chapterId }.toSet().size)
	}

	private fun assertLayout(list: DoublePageList, expected: String) {
		val actual = list.items.chunked(2).joinToString(" | ") { spread ->
			spread.joinToString(" ") { if (it.isSpacer) "_" else "${it.chapterId}:${it.index}" }
		}
		assertEquals(expected, actual)
	}

	/** Pages of consecutive chapters with ids 1, 2, ... having the given page counts */
	private fun chapters(vararg counts: Int): List<ReaderPage> = counts.flatMapIndexed { ch, count ->
		List(count) { page(ch + 1L, it) }
	}

	private fun page(chapterId: Long, index: Int) = ReaderPage(
		id = chapterId * 100 + index,
		url = "url/$chapterId/$index",
		preview = null,
		chapterId = chapterId,
		index = index,
		source = TestMangaSource,
	)
}
