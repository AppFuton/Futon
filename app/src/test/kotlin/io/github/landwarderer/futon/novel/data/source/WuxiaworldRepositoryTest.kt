package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class WuxiaworldRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(15, TimeUnit.SECONDS)
		.build()

	private val cache = mock(MemoryContentCache::class.java)
	private val repository = WuxiaworldRepository(cache, httpClient)

	@Test
	fun testGetList_novelsDoNotHaveOngoingAsTitle() = runBlocking {
		val novels = repository.getList(0, null, null)
		assertTrue("Catalog should return novels", novels.isNotEmpty())

		for (novel in novels) {
			assertFalse("Novel title must not be 'Ongoing'", novel.title.equals("Ongoing", ignoreCase = true))
			assertFalse("Novel title must not be 'Completed'", novel.title.equals("Completed", ignoreCase = true))
			assertTrue("Novel title should be non-blank", novel.title.isNotBlank())
			assertTrue("Novel url should contain /novel/", novel.url.contains("/novel/"))
		}
	}

	@Test
	fun testGetDetails_populatesNovelDetailsAndChapters() = runBlocking {
		val novels = repository.getList(0, null, null)
		assertTrue(novels.isNotEmpty())

		val firstNovel = novels.first()
		val detailed = repository.getDetailsImpl(firstNovel)

		println("Detailed novel title: ${detailed.title}")
		println("Detailed novel author: ${detailed.authors}")
		println("Detailed novel state: ${detailed.state}")
		println("Detailed novel rating: ${detailed.rating}")
		println("Detailed novel tags size: ${detailed.tags.size}")
		println("Detailed novel chapters count: ${detailed.chapters?.size}")

		assertFalse("Detailed title must not be 'Ongoing'", detailed.title.equals("Ongoing", ignoreCase = true))
		assertNotNull("Detailed novel must have non-null description", detailed.description)
		assertTrue("Detailed novel must have non-blank description", detailed.description?.isNotBlank() == true)

		val chapters = detailed.chapters
		assertNotNull("Detailed novel must have chapters", chapters)
		assertTrue("Detailed novel chapters list should not be empty", chapters?.isNotEmpty() == true)
	}
}
