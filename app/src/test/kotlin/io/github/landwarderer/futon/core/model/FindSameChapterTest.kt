package io.github.landwarderer.futon.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaChapter

class FindSameChapterTest {

	@Test
	fun `same url in same branch wins`() {
		val previous = chapter(1, "a/1", 1f, "en")
		val list = listOf(chapter(10, "a/1", 5f, "fr"), chapter(11, "a/1", 1f, "en"))
		assertEquals(11L, list.findSameChapter(previous)?.id)
	}

	@Test
	fun `same number in same branch when url changed`() {
		val previous = chapter(1, "mirror1/1", 3f, "en")
		val list = listOf(chapter(10, "mirror2/2", 2f, "en"), chapter(11, "mirror2/3", 3f, "en"), chapter(12, "x", 3f, "fr"))
		assertEquals(11L, list.findSameChapter(previous)?.id)
	}

	@Test
	fun `ambiguous number returns null`() {
		val previous = chapter(1, "old", 3f, "en")
		val list = listOf(chapter(10, "new1", 3f, "en"), chapter(11, "new2", 3f, "en"))
		assertNull(list.findSameChapter(previous))
	}

	@Test
	fun `zero number requires the same title`() {
		val previous = chapter(1, "old", 0f, null, title = "Extra")
		val list = listOf(chapter(10, "new1", 0f, null, title = "Other"), chapter(11, "new2", 0f, null, title = "Extra"))
		assertEquals(11L, list.findSameChapter(previous)?.id)
	}

	@Test
	fun `same url in another branch as last resort`() {
		val previous = chapter(1, "a/1", 1f, "old branch")
		val list = listOf(chapter(10, "a/1", 1f, "new branch"), chapter(11, "a/2", 2f, "new branch"))
		assertEquals(10L, list.findSameChapter(previous)?.id)
	}

	private fun chapter(id: Long, url: String, number: Float, branch: String?, title: String? = null) = MangaChapter(
		id = id, title = title, number = number, volume = 0, url = url, uploadDate = 0L,
		scanlator = null, branch = branch, source = TestMangaSource,
	)
}
