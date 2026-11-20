package io.github.landwarderer.futon.tracker.domain

import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.time.Instant

class ObserveUpdatesMapUseCaseTest {

	@Test
	fun checkingAMangaDoesNotChangeTheMap() {
		val before = listOf(tracking(1, new = 3, checkedAt = 100L), tracking(2, new = 1, checkedAt = 100L))
		val after = listOf(tracking(1, new = 3, checkedAt = 900L), tracking(2, new = 1, checkedAt = 950L))
		assertEquals(mapContent(before), mapContent(after))
	}

	@Test
	fun reorderingDoesNotChangeTheMap() {
		val first = tracking(1, new = 3)
		val second = tracking(2, new = 1)
		assertEquals(mapContent(listOf(first, second)), mapContent(listOf(second, first)))
	}

	@Test
	fun aNewChapterChangesTheMap() {
		assertNotEquals(mapContent(listOf(tracking(1, new = 3))), mapContent(listOf(tracking(1, new = 4))))
	}

	@Test
	fun aNewlyUpdatedMangaChangesTheMap() {
		val before = listOf(tracking(1, new = 3))
		assertNotEquals(mapContent(before), mapContent(before + tracking(2, new = 1)))
	}

	private fun tracking(id: Long, new: Int, checkedAt: Long = 0L) = MangaTracking(
		manga = Manga(
			id = id,
			title = "Manga $id",
			altTitles = emptySet(),
			url = "/$id",
			publicUrl = "https://example.com/$id",
			rating = 0f,
			contentRating = null,
			coverUrl = null,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			source = MangaParserSource.entries.first(),
		),
		lastChapterId = 0L,
		lastCheck = Instant.ofEpochMilli(checkedAt),
		lastChapterDate = null,
		newChapters = new,
	)
}
