package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.details.data.MangaDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage

class SmartResumeChapterLoadingTest {
    @Test
    fun `empty replacement preserves pages and can retry successfully`() = runTest {
        val (loader, repository, next) = fixture()
        val original = loader.snapshot()
        `when`(repository.getPages(next)).thenReturn(emptyList())
        assertFalse(loader.loadSingleChapter(2, keepCurrentOnEmpty = true))
        assertEquals(original, loader.snapshot())
        `when`(repository.getPages(next)).thenReturn(listOf(page(2)))
        assertTrue(loader.loadSingleChapter(2, keepCurrentOnEmpty = true))
        assertEquals(listOf(2L), loader.snapshot().map { it.chapterId })
    }

    @Test
    fun `exceptions and cancellation preserve original pages`() = runTest {
        for (failure in listOf(IllegalStateException("offline"), CancellationException())) {
            val (loader, repository, next) = fixture()
            val original = loader.snapshot()
            `when`(repository.getPages(next)).thenThrow(failure)
            try {
                loader.loadSingleChapter(2, keepCurrentOnEmpty = true)
                throw AssertionError("Expected failure")
            } catch (e: IllegalStateException) {
                assertEquals(failure, e)
                assertEquals(original, loader.snapshot())
            }
        }
    }

    private suspend fun fixture(): Triple<ChaptersLoader, MangaRepository, MangaChapter> {
        val repository = mock(MangaRepository::class.java)
        val factory = mock(MangaRepository.Factory::class.java)
        `when`(factory.create(TestMangaSource)).thenReturn(repository)
        val chapters = (1L..2L).map { id ->
            MangaChapter(
                id = id, title = "Chapter $id", number = id.toFloat(), volume = 0,
                url = "https://example.org/$id", uploadDate = 0L,
                scanlator = null, branch = null, source = TestMangaSource,
            )
        }
        `when`(repository.getPages(chapters[0])).thenReturn(listOf(page(1)))
        val details = mock(MangaDetails::class.java)
        `when`(details.allChapters).thenReturn(chapters)
        val loader = ChaptersLoader(factory)
        loader.init(details)
        loader.loadSingleChapter(1)
        return Triple(loader, repository, chapters[1])
    }

    private fun page(id: Long) = MangaPage(id, "https://example.org/$id", null, TestMangaSource)
}
