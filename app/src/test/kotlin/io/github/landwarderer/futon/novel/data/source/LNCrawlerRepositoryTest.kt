package io.github.landwarderer.futon.novel.data.source

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.mockito.Mockito.mock

class LNCrawlerRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val url = chain.request().url.toString()
			val (contentType, responseBody) = when {
				url.contains("/novels/search/") && url.contains("query=primal") -> {
					"application/json; charset=utf-8" to buildSearchJson()
				}
				url.contains("/novels/search/") -> {
					"application/json; charset=utf-8" to buildCatalogJson()
				}
				url.contains("/novels/the-primal-hunter/novelhulkcom/chapters/") -> {
					"application/json; charset=utf-8" to buildChaptersJson()
				}
				url.contains("/novels/the-primal-hunter/novelhulkcom/chapter/1/") -> {
					"application/json; charset=utf-8" to buildChapter1Json()
				}
				url.contains("/novels/the-primal-hunter/novelhulkcom/") -> {
					"application/json; charset=utf-8" to buildSourceDetailJson()
				}
				url.contains("/novels/the-primal-hunter/") -> {
					"application/json; charset=utf-8" to buildNovelDetailJson()
				}
				url.contains("/sitemap-novels.xml") -> {
					"application/xml; charset=utf-8" to buildSitemapNovelsXml()
				}
				url.contains("/sitemap.xml") -> {
					"application/xml; charset=utf-8" to buildSitemapIndexXml()
				}
				else -> "application/json; charset=utf-8" to "{}"
			}
			Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(responseBody.toResponseBody(contentType.toMediaType()))
				.build()
		}
		.build()

	private val cache = mock(MemoryContentCache::class.java)
	private val repository = LNCrawlerRepository(cache, httpClient)

	@Test
	fun testGetList_popularityCatalog() = runBlocking {
		val novels = repository.getList(0, SortOrder.POPULARITY, null)
		assertTrue("Catalog should return items", novels.isNotEmpty())
		assertEquals(2, novels.size)

		val first = novels.first()
		assertEquals("The Primal Hunter", first.title)
		assertEquals("https://lncrawler.monster/novels/the-primal-hunter/", first.url)
		assertEquals("https://api.lncrawler.monster/media/covers/the-primal-hunter.min.jpg", first.coverUrl)
		assertEquals(4.8f, first.rating)
		assertTrue("Authors should contain Zogarth", first.authors.contains("Zogarth"))
		assertTrue("Tags should contain LitRPG", first.tags.any { it.title == "LitRPG" })

		val second = novels[1]
		assertEquals("Shadow Slave", second.title)
		assertEquals("https://lncrawler.monster/novels/shadow-slave/", second.url)
	}

	@Test
	fun testGetList_search() = runBlocking {
		val filter = MangaListFilter(query = "primal")
		val novels = repository.getList(0, null, filter)
		assertTrue("Search should return items", novels.isNotEmpty())
		assertEquals(1, novels.size)
		assertEquals("The Primal Hunter", novels.first().title)
	}

	@Test
	fun testGetDetails_withChapters() = runBlocking {
		val dummyManga = Manga(
			id = 12345L,
			title = "The Primal Hunter",
			altTitles = emptySet(),
			url = "https://lncrawler.monster/novels/the-primal-hunter/",
			publicUrl = "https://lncrawler.monster/novels/the-primal-hunter/",
			rating = 0f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = NovelParserSource.LNCRAWLER,
		)

		val detailed = repository.getDetailsImpl(dummyManga)
		assertEquals("The Primal Hunter", detailed.title)
		assertEquals(4.8f, detailed.rating)
		assertTrue("Author should be Zogarth", detailed.authors.contains("Zogarth"))
		assertTrue("Tags should contain LitRPG", detailed.tags.any { it.title == "LitRPG" })
		assertEquals(MangaState.ONGOING, detailed.state)
		assertTrue("Description should be extracted", detailed.description?.contains("Jake, an apparently normal office worker") == true)

		val chapters = detailed.chapters
		assertNotNull("Chapters must not be null", chapters)
		assertEquals(2, chapters!!.size)

		assertEquals("Chapter 1: The Initiation", chapters[0].title)
		assertEquals(1f, chapters[0].number)
		assertEquals(1, chapters[0].volume)
		assertEquals("Novel Hulk", chapters[0].scanlator)

		assertEquals("Chapter 2: The Tutorial Begins", chapters[1].title)
		assertEquals(2f, chapters[1].number)
	}

	@Test
	fun testGetPages_cleansHtml() = runBlocking {
		val dummyChapter = MangaChapter(
			id = 9999L,
			title = "Chapter 1",
			number = 1f,
			volume = 1,
			url = "https://api.lncrawler.monster/novels/the-primal-hunter/novelhulkcom/chapter/1/",
			uploadDate = 0L,
			source = NovelParserSource.LNCRAWLER,
			scanlator = "Novel Hulk",
			branch = "Novel Hulk",
		)

		val pages = repository.getPagesImpl(dummyChapter)
		assertEquals(1, pages.size)
		val content = pages.first().url

		assertTrue("Must contain story content", content.contains("Jake woke up with a pounding headache."))
		assertFalse("Scripts must be stripped", content.contains("alert("))
		assertFalse("Styles must be stripped", content.contains("body { color: red; }"))
	}

	@Test
	fun testParseNovelAndSourceSlugs() {
		val pair1 = repository.parseNovelAndSourceSlugs("https://lncrawler.monster/novels/the-primal-hunter/")
		assertEquals("the-primal-hunter", pair1.first)
		assertEquals(null, pair1.second)

		val pair2 = repository.parseNovelAndSourceSlugs("https://lncrawler.monster/novels/the-primal-hunter/novelhulkcom/")
		assertEquals("the-primal-hunter", pair2.first)
		assertEquals("novelhulkcom", pair2.second)

		val pair3 = repository.parseNovelAndSourceSlugs("https://lncrawler.monster/novels/the-primal-hunter/novelhulkcom/chapterlist/")
		assertEquals("the-primal-hunter", pair3.first)
		assertEquals("novelhulkcom", pair3.second)

		val pair4 = repository.parseNovelAndSourceSlugs("https://api.lncrawler.monster/novels/the-primal-hunter/novelhulkcom/")
		assertEquals("the-primal-hunter", pair4.first)
		assertEquals("novelhulkcom", pair4.second)
	}

	@Test
	fun testSitemapIndexAndNovels() = runBlocking {
		val sitemaps = repository.fetchSitemapIndex()
		assertEquals(2, sitemaps.size)
		assertTrue("Should contain sitemap-novels.xml", sitemaps.contains("https://lncrawler.monster/sitemap-novels.xml"))

		val novels = repository.fetchSitemapNovels()
		assertEquals(2, novels.size)
		assertTrue("Should contain novel url", novels.contains("https://lncrawler.monster/novels/the-primal-hunter/"))
	}

	companion object {
		private fun buildCatalogJson(): String = """
			{
				"count": 2,
				"results": [
					{
						"title": "The Primal Hunter",
						"slug": "the-primal-hunter",
						"avg_rating": 4.8,
						"prefered_source": {
							"source_slug": "novelhulkcom",
							"cover_min_url": "https://api.lncrawler.monster/media/covers/the-primal-hunter.min.jpg",
							"cover_url": "https://api.lncrawler.monster/media/covers/the-primal-hunter.jpg",
							"authors": ["Zogarth"],
							"tags": ["Action", "LitRPG"]
						}
					},
					{
						"title": "Shadow Slave",
						"slug": "shadow-slave",
						"avg_rating": 4.9,
						"prefered_source": {
							"source_slug": "novelfirenet",
							"cover_min_url": "https://api.lncrawler.monster/media/covers/shadow-slave.min.jpg",
							"authors": ["Guiltythree"],
							"tags": ["Action", "Fantasy"]
						}
					}
				]
			}
		""".trimIndent()

		private fun buildSearchJson(): String = """
			{
				"count": 1,
				"results": [
					{
						"title": "The Primal Hunter",
						"slug": "the-primal-hunter",
						"avg_rating": 4.8,
						"prefered_source": {
							"source_slug": "novelhulkcom",
							"cover_min_url": "https://api.lncrawler.monster/media/covers/the-primal-hunter.min.jpg",
							"authors": ["Zogarth"],
							"tags": ["Action", "LitRPG"]
						}
					}
				]
			}
		""".trimIndent()

		private fun buildNovelDetailJson(): String = """
			{
				"title": "The Primal Hunter",
				"slug": "the-primal-hunter",
				"avg_rating": 4.8,
				"prefered_source": {
					"source_slug": "novelhulkcom",
					"cover_url": "https://api.lncrawler.monster/media/covers/the-primal-hunter.jpg"
				},
				"sources": [
					{
						"source_slug": "novelhulkcom",
						"source_name": "Novel Hulk"
					}
				]
			}
		""".trimIndent()

		private fun buildSourceDetailJson(): String = """
			{
				"source_name": "Novel Hulk",
				"source_slug": "novelhulkcom",
				"status": "Ongoing",
				"cover_url": "https://api.lncrawler.monster/media/covers/the-primal-hunter.jpg",
				"authors": ["Zogarth"],
				"tags": ["Action", "LitRPG"],
				"synopsis": "<p>Jake, an apparently normal office worker, finds himself thrust into a vast multiverse filled with gods, beasts, and magic.</p>"
			}
		""".trimIndent()

		private fun buildChaptersJson(): String = """
			{
				"page": 1,
				"total_pages": 1,
				"total_chapters": 2,
				"chapters": [
					{
						"chapter_id": 1,
						"title": "Chapter 1: The Initiation",
						"volume": 1
					},
					{
						"chapter_id": 2,
						"title": "Chapter 2: The Tutorial Begins",
						"volume": 1
					}
				]
			}
		""".trimIndent()

		private fun buildChapter1Json(): String = """
			{
				"chapter_id": 1,
				"title": "Chapter 1: The Initiation",
				"body": "<style>body { color: red; }</style><p>Jake woke up with a pounding headache.</p><script>alert('malicious')</script>"
			}
		""".trimIndent()

		private fun buildSitemapIndexXml(): String = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
				<sitemap><loc>https://lncrawler.monster/sitemap-novels.xml</loc></sitemap>
				<sitemap><loc>https://lncrawler.monster/sitemap-sources.xml</loc></sitemap>
			</sitemapindex>
		""".trimIndent()

		private fun buildSitemapNovelsXml(): String = """
			<?xml version="1.0" encoding="UTF-8"?>
			<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
				<url><loc>https://lncrawler.monster/novels/the-primal-hunter/</loc></url>
				<url><loc>https://lncrawler.monster/novels/shadow-slave/</loc></url>
			</urlset>
		""".trimIndent()
	}
}
