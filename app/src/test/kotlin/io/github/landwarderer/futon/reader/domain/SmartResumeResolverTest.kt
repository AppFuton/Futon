package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.MangaHistory
import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.prefs.ChapterCompletionMode
import io.github.landwarderer.futon.reader.ui.ReaderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaChapter
import java.time.Instant

class SmartResumeResolverTest {
    private val percentage = ChapterCompletionRule(ChapterCompletionMode.PERCENTAGE, 90, 1)
    private val pages = ChapterCompletionRule(ChapterCompletionMode.PAGES_REMAINING, 90, 1)

    @Test
    fun `percentage boundary counts the current page`() {
        assertTrue(percentage.isCompleted(13, 15))
        assertTrue(percentage.isCompleted(14, 15))
        assertFalse(percentage.isCompleted(12, 15))
        assertTrue(percentage.isCompleted(8, 10))
        assertFalse(percentage.isCompleted(7, 10))
        assertFalse(percentage.copy(percentage = 95).isCompleted(13, 15))
        assertTrue(percentage.copy(percentage = 100).isCompleted(14, 15))
    }

    @Test
    fun `pages remaining allows the final and second to last page`() {
        assertTrue(pages.isCompleted(14, 15))
        assertTrue(pages.isCompleted(13, 15))
        assertFalse(pages.isCompleted(12, 15))
        assertFalse(pages.copy(pagesRemaining = 0).isCompleted(13, 15))
        assertTrue(pages.copy(pagesRemaining = 0).isCompleted(14, 15))
    }

    @Test
    fun `short chapters invalid positions and large counts`() {
        for (rule in listOf(percentage, pages)) {
            assertTrue(rule.isCompleted(0, 1))
            assertFalse(rule.isCompleted(-1, 15))
            assertFalse(rule.isCompleted(15, 15))
            assertFalse(rule.isCompleted(0, 0))
            assertTrue(rule.isCompleted(Int.MAX_VALUE - 1, Int.MAX_VALUE))
        }
        assertFalse(percentage.copy(percentage = 101).isCompleted(14, 15))
        assertFalse(pages.copy(pagesRemaining = -1).isCompleted(14, 15))
    }

    @Test
    fun `previously latest advances only one chapter and resets scroll`() = runTest {
        val resolver = resolver()
        val loaded = mutableListOf<Long>()
        assertEquals(ReaderState(30, 0, 0), resolver.resolve(ids(32), 15, false) { loaded += it; true })
        assertEquals(listOf(30L), loaded)
    }

    @Test
    fun `old chapter existing successor and unfinished positions never advance`() = runTest {
        for (history in listOf(history(count = 30), history(page = 12), history(count = 0))) {
            val start = ReaderState(history)
            val resolver = SmartResumeResolver(start, history, percentage)
            assertEquals(start, resolver.resolve(ids(32), 15, false) { error("Must not load") })
        }
    }

    @Test
    fun `explicit restored recovered and new starts have no eligible history`() = runTest {
        val start = ReaderState(29, 14, 120)
        val resolver = SmartResumeResolver(start, null, percentage)
        assertEquals(start, resolver.resolve(ids(30), 15, false) { error("Must not load") })
    }

    @Test
    fun `missing chapter and different branch do not advance`() = runTest {
        assertEquals(ReaderState(history()), resolver().resolve(listOf(100, 101), 15, true) { error("Wrong branch") })
        assertEquals(ReaderState(history()), resolver().resolve(ids(28), 15, true) { error("Missing chapter") })
        assertEquals(ReaderState(history()), resolver().resolve(ids(29), 15, true) { error("No new chapter") })
    }

    @Test
    fun `a decision cannot jump after an update`() = runTest {
        val resolver = resolver()
        assertEquals(ReaderState(history()), resolver.resolve(ids(29), 15, true) { error("No successor") })
        assertEquals(ReaderState(history()), resolver.resolve(ids(30), 15, true) { error("Already decided") })
    }

    @Test
    fun `waiting holds the original history until updated details arrive`() = runTest {
        val saved = history()
        val resolver = SmartResumeResolver(ReaderState(saved), saved, percentage)
        assertNull(resolver.resolve(ids(29), 15, false) { error("Not ready") })
        assertNull(resolver.resolve(ids(29), 15, false) { error("Still not ready") })
        assertEquals(ReaderState(30, 0, 0), resolver.resolve(ids(32), 15, true) { true })
        assertEquals(29, saved.chaptersCount)
        assertEquals(ReaderState(30, 0, 0), resolver.resolve(ids(33), 15, true) { error("Second jump") })
    }

    @Test
    fun `a known successor advances without waiting for the update`() = runTest {
        assertEquals(ReaderState(30, 0, 0), resolver().resolve(ids(30), 15, false) { true })
    }

    @Test
    fun `starts that cannot advance never wait`() = runTest {
        for (history in listOf(history(page = 12), history(count = 30), history(count = 0))) {
            val start = ReaderState(history)
            val resolver = SmartResumeResolver(start, history, percentage)
            assertEquals(start, resolver.resolve(ids(29), 15, false) { error("Must not load") })
        }
        val start = ReaderState(29, 14, 120)
        assertEquals(start, SmartResumeResolver(start, null, percentage).resolve(ids(29), 15, false) { error("") })
    }

    @Test
    fun `a chapter with a new id still advances`() = runTest {
        // The source renumbered every chapter id: chapter 29 is now 129, the new chapter 130
        val renumbered = (101L..130L).toList()
        assertEquals(
            ReaderState(130, 0, 0),
            resolver().resolve(renumbered, 15, true, chapterId = 129) { true },
        )
    }

    @Test
    fun `the opened chapter is found again under a new id`() {
        val original = chapter(29, url = "/c/29")
        val renumbered = listOf(chapter(128, url = "/c/28"), chapter(129, url = "/c/29"), chapter(130, url = "/c/30"))
        assertEquals(129L, renumbered.findResumeChapter(original)?.id)
        assertEquals(29L, listOf(original, chapter(30, url = "/c/30")).findResumeChapter(original)?.id)
        // Ambiguous: two chapters with the same number and a different link
        val ambiguous = listOf(chapter(1, url = "/a", number = 29f), chapter(2, url = "/b", number = 29f))
        assertNull(ambiguous.findResumeChapter(chapter(29, url = "/c/29", number = 29f)))
    }

    @Test
    fun `normal flow completion can release a deferred start`() = runTest {
        val resolver = resolver()
        assertNull(resolver.resolve(ids(29), 15, false) { error("No successor") })
        assertEquals(ReaderState(history()), resolver.resolve(ids(29), 15, true) { error("No successor") })
    }

    @Test
    fun `update failure permanently releases original state`() = runTest {
        val resolver = resolver()
        assertNull(resolver.resolve(ids(29), 15, false) { error("Not ready") })
        assertFalse(resolver.isResolved)
        assertEquals(ReaderState(history()), resolver.fallback())
        assertTrue(resolver.isResolved)
        assertEquals(ReaderState(history()), resolver.resolve(ids(30), 15, true) { error("Already fell back") })
    }

    @Test
    fun `empty and failing successors fall back to the exact position`() = runTest {
        assertEquals(ReaderState(history()), resolver().resolve(ids(30), 15, true) { false })
        assertEquals(ReaderState(history()), resolver().resolve(ids(30), 15, true) { throw IllegalStateException() })
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates`() = runTest {
        resolver().resolve(ids(30), 15, true) { throw CancellationException() }
        Unit
    }

    @Test
    fun `unknown completion mode defaults to percentage`() {
        assertEquals(ChapterCompletionMode.PERCENTAGE, ChapterCompletionMode.from(null))
        assertEquals(ChapterCompletionMode.PERCENTAGE, ChapterCompletionMode.from("invalid"))
        assertEquals(ChapterCompletionMode.PAGES_REMAINING, ChapterCompletionMode.from("PAGES_REMAINING"))
    }

    private fun resolver() = SmartResumeResolver(ReaderState(history()), history(), percentage)

    private fun chapter(id: Long, url: String, number: Float = id.toFloat()) = MangaChapter(
        id = id, title = "Chapter $id", number = number, volume = 0,
        url = url, uploadDate = 0L, scanlator = null, branch = null, source = TestMangaSource,
    )

    private fun history(page: Int = 13, count: Int = 29) = MangaHistory(
        Instant.EPOCH, Instant.EPOCH, 29, page, 120, 1f, count,
    )

    private fun ids(count: Int) = (1L..count.toLong()).toList()
}
