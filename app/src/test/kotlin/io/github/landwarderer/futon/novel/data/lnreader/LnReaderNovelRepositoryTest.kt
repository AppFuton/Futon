package io.github.landwarderer.futon.novel.data.lnreader.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.novel.data.lnreader.model.LnChapterItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnNovelItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.model.LnSourceNovel
import io.github.landwarderer.futon.novel.data.lnreader.runtime.LnPluginRuntime

class LnReaderNovelRepositoryTest {

	private lateinit var runtime: LnPluginRuntime
	private lateinit var repository: LnReaderNovelRepository
	private val fakePlugin = LnPlugin(
		id = "mock_source",
		name = "Mock Source",
		site = "https://mocksite.org",
		lang = "English",
		version = "1.0.0",
		url = "https://mocksite.org/index.js",
		iconUrl = "https://mocksite.org/icon.png",
	)

	private val mockSource = object : MangaSource {
		override val name: String = fakePlugin.id
	}

	@Before
	fun setUp() {
		runtime = mock(LnPluginRuntime::class.java)
		val cache = mock(MemoryContentCache::class.java)
		repository = LnReaderNovelRepository(cache, mockSource, fakePlugin, runtime)
	}

	@Test
	fun testGetListPopular() = runTest {
		`when`(runtime.getPopularNovels(1)).thenReturn(
			listOf(
				LnNovelItem(name = "Solo Leveling", path = "/novel/solo-leveling", cover = "https://mocksite.org/solo.jpg"),
				LnNovelItem(name = "Omniscient Reader", path = "/novel/orv", cover = "https://mocksite.org/orv.jpg"),
			)
		)

		val result = repository.getList(offset = 0, order = null, filter = null)

		assertEquals(2, result.size)
		assertEquals("Solo Leveling", result[0].title)
		assertEquals("/novel/solo-leveling", result[0].url)
		assertEquals("https://mocksite.org/novel/solo-leveling", result[0].publicUrl)
		assertEquals("https://mocksite.org/solo.jpg", result[0].coverUrl)
		assertEquals("Omniscient Reader", result[1].title)
	}

	@Test
	fun testGetDetailsImpl() = runTest {
		@Suppress("DEPRECATION")
		val seedManga = Manga(
			id = 12345L,
			title = "Solo Leveling",
			altTitle = null,
			url = "/novel/solo-leveling",
			publicUrl = "https://mocksite.org/novel/solo-leveling",
			rating = 0f,
			isNsfw = false,
			coverUrl = "https://mocksite.org/solo.jpg",
			tags = emptySet(),
			state = null,
			author = "Chugong",
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = mockSource,
		)

		`when`(runtime.parseNovel("/novel/solo-leveling")).thenReturn(
			LnSourceNovel(
				name = "Solo Leveling",
				path = "/novel/solo-leveling",
				cover = "https://mocksite.org/solo_hd.jpg",
				author = "Chugong",
				artist = "DUBU",
				status = "Completed",
				summary = "10 years ago, after the Gate opened...",
				genres = "Action, Fantasy, System",
				chapters = listOf(
					LnChapterItem(name = "Chapter 1", path = "/chapter-1", chapterNumber = 1f, releaseTime = "2020-01-01"),
					LnChapterItem(name = "Chapter 2", path = "/chapter-2", chapterNumber = 2f, releaseTime = "2020-01-02"),
				),
			)
		)

		val details = repository.getDetailsImpl(seedManga)

		assertEquals("10 years ago, after the Gate opened...", details.description)
		assertEquals("https://mocksite.org/solo_hd.jpg", details.coverUrl)
		assertTrue(details.authors.contains("Chugong"))
		assertEquals(3, details.tags.size)
		assertNotNull(details.chapters)
		assertEquals(2, details.chapters?.size)
		assertEquals("Chapter 1", details.chapters?.get(0)?.title)
		assertEquals(1f, details.chapters?.get(0)?.number)
	}

	@Test
	fun testDynamicPaginationTracksDifferentPageSizes() = runTest {
		`when`(runtime.getPopularNovels(1)).thenReturn(
			(1..10).map { LnNovelItem(name = "Novel $it", path = "/novel/$it", cover = null) },
		)
		`when`(runtime.getPopularNovels(2)).thenReturn(
			(11..20).map { LnNovelItem(name = "Novel $it", path = "/novel/$it", cover = null) },
		)

		val firstBatch = repository.getList(offset = 0, order = null, filter = null)
		assertEquals(10, firstBatch.size)
		assertEquals("Novel 1", firstBatch[0].title)

		val secondBatch = repository.getList(offset = 10, order = null, filter = null)
		assertEquals(10, secondBatch.size)
		assertEquals("Novel 11", secondBatch[0].title)
	}

	@Test
	fun testGetPagesImpl() = runTest {
		val chapter = MangaChapter(
			id = 999L,
			title = "Chapter 1",
			number = 1f,
			volume = 0,
			url = "/chapter-1",
			uploadDate = 0L,
			source = mockSource,
			scanlator = null,
			branch = null,
		)

		`when`(runtime.parseChapter("/chapter-1")).thenReturn("<p>It was a dark and stormy night...</p>")

		val pages = repository.getPagesImpl(chapter)

		assertEquals(1, pages.size)
		assertEquals("novel_content://<p>It was a dark and stormy night...</p>", pages[0].url)
	}
}

