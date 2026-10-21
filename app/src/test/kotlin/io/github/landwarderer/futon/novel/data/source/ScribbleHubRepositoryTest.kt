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

class ScribbleHubRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val url = chain.request().url.toString()
			val (contentType, responseBody) = when {
				url.contains("sh=shadow") -> {
					"text/html; charset=utf-8" to buildSearchHtml()
				}
				url.contains("pg=2") -> {
					"text/html; charset=utf-8" to buildCatalogPage2Html()
				}
				url.contains("/series-finder/") -> {
					"text/html; charset=utf-8" to buildCatalogPage1Html()
				}
				url.contains("/read/") -> {
					"text/html; charset=utf-8" to buildReaderChapterHtml()
				}
				url.contains("/series/") -> {
					"text/html; charset=utf-8" to buildNovelDetailsHtml()
				}
				else -> "text/html; charset=utf-8" to "<html><body></body></html>"
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
	private val repository = ScribbleHubRepository(cache, httpClient)

	@Test
	fun testGetList_popularityCatalog() = runBlocking {
		val novels = repository.getList(0, SortOrder.POPULARITY, null)
		assertTrue("Catalog should return novels", novels.isNotEmpty())
		assertEquals(2, novels.size)

		val first = novels.first()
		assertEquals("The Young Master in the Shadows", first.title)
		assertEquals("https://www.scribblehub.com/series/413997/the-young-master-in-the-shadows/", first.url)
		assertEquals("https://cdn.scribblehub.com/seriesimg/mid/20/mid_413997.jpg", first.coverUrl)
		assertEquals(4.4f, first.rating)
		assertTrue("Author should be Akaichi", first.authors.contains("Akaichi"))
		assertTrue("Tags should contain Action", first.tags.any { it.title == "Action" })
		assertTrue("Description should be present", first.description?.contains("He has returned to the past") == true)
	}

	@Test
	fun testGetList_paginationPage2() = runBlocking {
		val novels = repository.getList(20, SortOrder.POPULARITY, null)
		assertTrue("Page 2 should return novels", novels.isNotEmpty())
		val first = novels.first()
		assertEquals("The Reincarnation of Alysara", first.title)
	}

	@Test
	fun testGetList_search() = runBlocking {
		val filter = MangaListFilter(query = "shadow")
		val novels = repository.getList(0, null, filter)
		assertTrue("Search should return novels", novels.isNotEmpty())
		val first = novels.first()
		assertEquals("The Young Master in the Shadows", first.title)
	}

	@Test
	fun testGetDetails_withChapters() = runBlocking {
		val dummyManga = Manga(
			id = 413997L,
			title = "The Young Master in the Shadows",
			altTitles = emptySet(),
			url = "https://www.scribblehub.com/series/413997/the-young-master-in-the-shadows/",
			publicUrl = "https://www.scribblehub.com/series/413997/the-young-master-in-the-shadows/",
			rating = 0f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = NovelParserSource.SCRIBBLEHUB,
		)

		val detailed = repository.getDetailsImpl(dummyManga)
		assertEquals("The Young Master in the Shadows", detailed.title)
		assertTrue("Author should be Akaichi", detailed.authors.contains("Akaichi"))
		assertTrue("Tags should contain Action", detailed.tags.any { it.title == "Action" })
		assertEquals(MangaState.ONGOING, detailed.state)
		assertTrue("Description should be extracted", detailed.description?.contains("intrigue story") == true)

		val chapters = detailed.chapters
		assertNotNull("Chapters must not be null", chapters)
		assertEquals(3, chapters!!.size)

		assertEquals("Chapter 01 : Reborn", chapters[0].title)
		assertEquals(1f, chapters[0].number)
		assertEquals("Chapter 02 : Awakening", chapters[1].title)
		assertEquals(2f, chapters[1].number)
		assertEquals("Chapter 03 : The Journey", chapters[2].title)
		assertEquals(3f, chapters[2].number)
	}

	@Test
	fun testGetPages_cleansReaderHtml() = runBlocking {
		val dummyChapter = MangaChapter(
			id = 414024L,
			title = "Chapter 01 : Reborn",
			number = 1f,
			volume = 0,
			url = "https://www.scribblehub.com/read/413997-the-young-master-in-the-shadows/chapter/414024/",
			uploadDate = 0L,
			source = NovelParserSource.SCRIBBLEHUB,
			scanlator = null,
			branch = null,
		)

		val pages = repository.getPagesImpl(dummyChapter)
		assertEquals(1, pages.size)
		val content = pages.first().url

		assertTrue("Must contain story content", content.contains("He has locked himself in the royal library"))
		assertFalse("Scripts must be removed", content.contains("alert("))
		assertFalse("Footnotes must be removed", content.contains("modern-footnotes-footnote"))
	}

	companion object {
		private fun buildCatalogPage1Html(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="search_main_box">
				<div class="search_img">
					<img src="https://cdn.scribblehub.com/seriesimg/mid/20/mid_413997.jpg" />
					<div class="search_ratings">(4.4)</div>
				</div>
				<div class="search_body">
					<div class="search_title"><a href="https://www.scribblehub.com/series/413997/the-young-master-in-the-shadows/">The Young Master in the Shadows</a></div>
					<div class="search_stats">
						<span class="nl_stat destp" title="Author"><span class="a_un_st"><a href="https://www.scribblehub.com/profile/72409/akaichi/">Akaichi</a></span></span>
					</div>
					<div class="search_genre">
						<a class="fic_genre search" href="https://www.scribblehub.com/genre/action/">Action</a>
					</div>
					He has returned to the past. And this time he will live up to his role as a young master.
				</div>
			</div>
			<div class="search_main_box">
				<div class="search_img">
					<img src="https://cdn.scribblehub.com/seriesimg/mid/10/mid_100.jpg" />
					<div class="search_ratings">(4.8)</div>
				</div>
				<div class="search_body">
					<div class="search_title"><a href="https://www.scribblehub.com/series/100/another-novel/">Another Novel</a></div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildCatalogPage2Html(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="search_main_box">
				<div class="search_img">
					<img src="https://cdn.scribblehub.com/seriesimg/mid/25/mid_278151.jpg" />
				</div>
				<div class="search_body">
					<div class="search_title"><a href="https://www.scribblehub.com/series/278151/the-reincarnation-of-alysara/">The Reincarnation of Alysara</a></div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildSearchHtml(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="search_main_box">
				<div class="search_img">
					<img src="https://cdn.scribblehub.com/seriesimg/mid/20/mid_413997.jpg" />
				</div>
				<div class="search_body">
					<div class="search_title"><a href="https://www.scribblehub.com/series/413997/the-young-master-in-the-shadows/">The Young Master in the Shadows</a></div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildNovelDetailsHtml(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="fic_title">The Young Master in the Shadows</div>
			<div class="auth_name_fic">Akaichi</div>
			<div class="fic_image"><img src="https://cdn.scribblehub.com/images/20/the-young-master-in-the-shadows_413997_1676269434.jpg" /></div>
			<div class="wi_fic_desc">This is a harem and intrigue story.</div>
			<div class="fic_genre">Action</div>
			<div class="wi_fic_show_stats">Status: Ongoing</div>
			<div class="wi_fic_table toc">
				<ol class="toc_ol">
					<li class="toc_w"><a class="toc_a" href="https://www.scribblehub.com/read/413997-the-young-master-in-the-shadows/chapter/414024/">Chapter 01 : Reborn</a></li>
					<li class="toc_w"><a class="toc_a" href="https://www.scribblehub.com/read/413997-the-young-master-in-the-shadows/chapter/414025/">Chapter 02 : Awakening</a></li>
					<li class="toc_w"><a class="toc_a" href="https://www.scribblehub.com/read/413997-the-young-master-in-the-shadows/chapter/414026/">Chapter 03 : The Journey</a></li>
				</ol>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildReaderChapterHtml(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div id="chp_raw" class="chp_raw">
				<p>He has locked himself in the royal library.</p>
				<div class="modern-footnotes-footnote">Note 1</div>
				<script>alert('test')</script>
			</div>
			</body>
			</html>
		""".trimIndent()
	}
}
