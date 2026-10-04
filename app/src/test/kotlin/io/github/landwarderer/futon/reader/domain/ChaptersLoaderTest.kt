package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.details.data.MangaDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage

class ChaptersLoaderTest {
    @Test
    fun `repeated forward and backward requests load only once`() = runTest {
        val fixture = fixture(3, 10)
        fixture.loader.loadSingleChapter(2)
        assertTrue(fixture.loader.loadPrevNextChapter(fixture.details, 2, true))
        assertFalse(fixture.loader.loadPrevNextChapter(fixture.details, 2, true))
        assertTrue(fixture.loader.loadPrevNextChapter(fixture.details, 2, false))
        assertFalse(fixture.loader.loadPrevNextChapter(fixture.details, 2, false))
        assertEquals(listOf(1L, 2L, 3L), fixture.loader.snapshot().map { it.chapterId }.distinct())
        fixture.chapters.forEach { verify(fixture.repository, times(1)).getPages(it) }
    }

    @Test
    fun `forward append trims old pages without losing boundary`() = runTest {
        val fixture = fixture(3, 70)
        fixture.loader.loadSingleChapter(1)
        fixture.loader.loadPrevNextChapter(fixture.details, 1, true)
        assertTrue(fixture.loader.loadPrevNextChapter(fixture.details, 2, true))
        assertEquals(listOf(2L, 3L), fixture.loader.snapshot().map { it.chapterId }.distinct())
        assertEquals(10, fixture.loader.snapshot().nextChapterPrefetchPages(2, 10).size)
    }

    @Test
    fun `missing and empty successors preserve current pages`() = runTest {
        val fixture = fixture(2, 10)
        fixture.loader.loadSingleChapter(1)
        val original = fixture.loader.snapshot()
        `when`(fixture.repository.getPages(fixture.chapters[1])).thenReturn(emptyList())
        assertFalse(fixture.loader.loadPrevNextChapter(fixture.details, 1, true))
        assertFalse(fixture.loader.loadPrevNextChapter(fixture.details, 2, true))
        assertEquals(original, fixture.loader.snapshot())
    }

    @Test
    fun `failed successor leaves current pages usable`() = runTest {
        val fixture = fixture(2, 10)
        fixture.loader.loadSingleChapter(1)
        val original = fixture.loader.snapshot()
        `when`(fixture.repository.getPages(fixture.chapters[1])).thenThrow(IllegalStateException("offline"))
        try {
            fixture.loader.loadPrevNextChapter(fixture.details, 1, true)
            throw AssertionError("Expected failure")
        } catch (_: IllegalStateException) {
            assertEquals(original, fixture.loader.snapshot())
        }
    }

    private suspend fun fixture(count: Int, pagesCount: Int): Fixture {
        val repository = mock(MangaRepository::class.java)
        val factory = mock(MangaRepository.Factory::class.java)
        `when`(factory.create(TestMangaSource)).thenReturn(repository)
        val chapters = List(count) { index ->
            MangaChapter(
                id = index + 1L, title = "Chapter ${index + 1}", number = index + 1f, volume = 0,
                url = "https://example.org/${index + 1}", uploadDate = 0L,
                scanlator = null, branch = null, source = TestMangaSource,
            )
        }
        for (chapter in chapters) {
            val pages = List(pagesCount) { index ->
                MangaPage(chapter.id * 1000 + index, "https://example.org/$index", null, TestMangaSource)
            }
            `when`(repository.getPages(chapter)).thenReturn(pages)
        }
        val details = mock(MangaDetails::class.java)
        `when`(details.allChapters).thenReturn(chapters)
        val loader = ChaptersLoader(factory)
        loader.init(details)
        return Fixture(loader, repository, details, chapters)
    }

    private data class Fixture(
        val loader: ChaptersLoader,
        val repository: MangaRepository,
        val details: MangaDetails,
        val chapters: List<MangaChapter>,
    )
}
