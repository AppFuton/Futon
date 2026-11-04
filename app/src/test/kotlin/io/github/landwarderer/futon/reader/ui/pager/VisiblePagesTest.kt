package io.github.landwarderer.futon.reader.ui.pager

import io.github.landwarderer.futon.core.model.TestMangaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisiblePagesTest {

    @Test
    fun `positions inside the list are reported unchanged`() {
        assertEquals(3..4, visibleAdapterPositions(3, 4, itemCount = 10))
        assertEquals(0..0, visibleAdapterPositions(0, 0, itemCount = 10))
    }

    @Test
    fun `NO_POSITION is ignored rather than clamped to the first page`() {
        assertNull(visibleAdapterPositions(-1, -1, itemCount = 10))
        assertNull(visibleAdapterPositions(-1, 4, itemCount = 10))
        assertNull(visibleAdapterPositions(3, -1, itemCount = 10))
    }

    @Test
    fun `an empty adapter reports nothing`() {
        assertNull(visibleAdapterPositions(0, 0, itemCount = 0))
    }

    @Test
    fun `a spread ending one past the last page is clamped to the last page`() {
        assertEquals(9..9, visibleAdapterPositions(9, 10, itemCount = 10))
    }

    @Test
    fun `stale position after a prepend resolves to the page on screen, not the previous chapter`() {
        // The adapter still shows chapter 39 while the view model has already prepended chapter 38.
        val adapterItems = chapter(39, 20)
        val content = chapter(38, 15) + chapter(39, 20)

        val positions = checkNotNull(visibleAdapterPositions(0, 0, adapterItems.size))
        val range = checkNotNull(
            content.visibleRangeOf(adapterItems[positions.first], adapterItems[positions.last]),
        )

        val current = content[(range.first + range.last) / 2]
        assertEquals(39L, current.chapterId)
        assertEquals(0, current.index)
        // Resolving the raw position against the new content is the original bug.
        assertEquals(38L, content[positions.first].chapterId)
    }

    @Test
    fun `NO_POSITION after a prepend does not report the previous chapter`() {
        val adapterItems = chapter(39, 20)
        assertNull(visibleAdapterPositions(-1, -1, adapterItems.size))
    }

    @Test
    fun `reversed modes report pages back to front but resolve to an ascending range`() {
        val content = chapter(39, 20)
        val adapterItems = content.reversed()

        val positions = checkNotNull(visibleAdapterPositions(4, 5, adapterItems.size))
        val range = content.visibleRangeOf(adapterItems[positions.first], adapterItems[positions.last])

        assertEquals(14..15, range)
    }

    @Test
    fun `a page that is no longer loaded resolves to nothing`() {
        val trimmed = chapter(39, 20) + chapter(40, 20)
        val removed = chapter(38, 15).last()

        assertNull(trimmed.visibleRangeOf(removed, removed))
        assertNull(trimmed.visibleRangeOf(removed, trimmed.first()))
    }

    @Test
    fun `pages are matched by chapter and index, not by object`() {
        val content = chapter(39, 20)
        val copy = content[7].copy(url = "https://example.org/reloaded")

        assertEquals(7..7, content.visibleRangeOf(copy, copy))
    }

    private fun chapter(id: Long, count: Int) = List(count) { index ->
        ReaderPage(id * 1000 + index, "https://example.org/$id/$index", null, id, index, TestMangaSource)
    }
}
