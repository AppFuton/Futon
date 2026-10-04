package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.reader.ui.pager.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPrefetchTest {
    @Test
    fun `prefetch starts at successor and respects the limit`() {
        val pages = chapter(29, 15) + chapter(30, 20)
        assertEquals(chapter(30, 20).take(10), pages.nextChapterPrefetchPages(29, 10))
    }

    @Test
    fun `short successor does not spill into another chapter`() {
        val pages = chapter(29, 15) + chapter(30, 2) + chapter(31, 15)
        assertEquals(chapter(30, 2), pages.nextChapterPrefetchPages(29, 10))
    }

    @Test
    fun `trimming older chapters does not change selection`() {
        val pages = chapter(28, 100) + chapter(29, 30) + chapter(30, 12)
        assertEquals(
            pages.nextChapterPrefetchPages(29, 10),
            pages.drop(100).nextChapterPrefetchPages(29, 10),
        )
    }

    @Test
    fun `missing boundary successor and empty snapshots are harmless`() {
        assertTrue(emptyList<ReaderPage>().nextChapterPrefetchPages(29, 10).isEmpty())
        assertTrue(chapter(29, 15).nextChapterPrefetchPages(29, 10).isEmpty())
        assertTrue(chapter(30, 15).nextChapterPrefetchPages(29, 10).isEmpty())
        assertTrue((chapter(29, 15) + chapter(30, 15)).nextChapterPrefetchPages(29, 0).isEmpty())
    }

    private fun chapter(id: Long, count: Int) = List(count) { index ->
        ReaderPage(id * 1000 + index, "https://example.org/$id/$index", null, id, index, TestMangaSource)
    }
}
