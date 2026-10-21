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

class FreeWebNovelRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val url = chain.request().url.toString()
			val (contentType, responseBody) = when {
				url.contains("ajax=chapters") && url.contains("page=2") -> {
					"application/json; charset=utf-8" to buildChaptersPage2Json()
				}
				url.contains("ajax=chapters") -> {
					"application/json; charset=utf-8" to buildChaptersPage1Json()
				}
				url.contains("/search") -> {
					"text/html; charset=utf-8" to buildSearchHtml()
				}
				url.contains("/sort/most-popular/2") -> {
					"text/html; charset=utf-8" to buildCatalogPage2Html()
				}
				url.contains("/sort/most-popular") -> {
					"text/html; charset=utf-8" to buildCatalogPage1Html()
				}
				url.contains("/novel/shadow-slave/chapter-1") -> {
					"text/html; charset=utf-8" to buildReaderChapterHtml()
				}
				url.contains("/novel/shadow-slave") -> {
					"text/html; charset=utf-8" to buildNovelDetailsHtml()
				}
				url.contains("/novel/short-novel") -> {
					"text/html; charset=utf-8" to buildShortNovelDetailsHtml()
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
	private val repository = FreeWebNovelRepository(cache, httpClient)

	@Test
	fun testGetList_popularityCatalog() = runBlocking {
		val novels = repository.getList(0, SortOrder.POPULARITY, null)
		assertTrue("Popularity catalog should return items", novels.isNotEmpty())
		assertEquals(2, novels.size)

		val first = novels.first()
		assertEquals("Shadow Slave", first.title)
		assertEquals("https://freewebnovel.com/novel/shadow-slave", first.url)
		assertEquals("https://freewebnovel.com/files/article/image/1/1991/1991s.jpg", first.coverUrl)
		assertEquals(4.6f, first.rating)
		assertTrue("Tags should contain Action", first.tags.any { it.title == "Action" })

		// Second item tests backwards compatibility with div.tit
		val second = novels[1]
		assertEquals("Invincible", second.title)
		assertEquals("https://freewebnovel.com/novel/invincible-novel", second.url)
	}

	@Test
	fun testGetList_paginationPage2() = runBlocking {
		val novels = repository.getList(20, SortOrder.POPULARITY, null)
		assertTrue("Page 2 should return items", novels.isNotEmpty())
		val first = novels.first()
		assertEquals("The Sovereign's Ascension", first.title)
		assertEquals("https://freewebnovel.com/novel/the-sovereigns-ascension", first.url)
	}

	@Test
	fun testGetList_search() = runBlocking {
		val filter = MangaListFilter(query = "shadow")
		val novels = repository.getList(0, null, filter)
		assertTrue("Search should return items", novels.isNotEmpty())
		val first = novels.first()
		assertEquals("Shadow Slave", first.title)
	}

	@Test
	fun testGetDetails_withAjaxChapters() = runBlocking {
		val dummyManga = Manga(
			id = 1991L,
			title = "Shadow Slave",
			altTitles = emptySet(),
			url = "https://freewebnovel.com/novel/shadow-slave",
			publicUrl = "https://freewebnovel.com/novel/shadow-slave",
			rating = 0f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = NovelParserSource.FREEWEBNOVEL,
		)

		val detailed = repository.getDetailsImpl(dummyManga)
		assertEquals("Shadow Slave", detailed.title)
		assertTrue("Author should be Guiltythree", detailed.authors.contains("Guiltythree"))
		assertTrue("Tags should contain Action", detailed.tags.any { it.title == "Action" })
		assertEquals(MangaState.ONGOING, detailed.state)
		assertTrue("Description should be extracted", detailed.description?.contains("Nightmare Spell") == true)

		val chapters = detailed.chapters
		assertNotNull("Chapters must not be null", chapters)
		assertEquals("Should have all 4 chapters across 2 pages", 4, chapters!!.size)

		assertEquals("Chapter 1: Nightmare Begins", chapters[0].title)
		assertEquals(1f, chapters[0].number)
		assertEquals("Chapter 2: Slave Caravan", chapters[1].title)
		assertEquals(2f, chapters[1].number)
		assertEquals("Chapter 3: The Strings of Fate", chapters[2].title)
		assertEquals(3f, chapters[2].number)
		assertEquals("Chapter 4: Mountain King", chapters[3].title)
		assertEquals(4f, chapters[3].number)
	}

	@Test
	fun testGetDetails_withDomFallbackChapters() = runBlocking {
		val dummyManga = Manga(
			id = 2000L,
			title = "Short Novel",
			altTitles = emptySet(),
			url = "https://freewebnovel.com/novel/short-novel",
			publicUrl = "https://freewebnovel.com/novel/short-novel",
			rating = 0f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = NovelParserSource.FREEWEBNOVEL,
		)

		val detailed = repository.getDetailsImpl(dummyManga)
		assertEquals(MangaState.FINISHED, detailed.state)
		val chapters = detailed.chapters
		assertNotNull("Chapters must not be null", chapters)
		assertEquals(2, chapters!!.size)
		assertEquals("Chapter 1: Short Intro", chapters[0].title)
		assertEquals("Chapter 2: Short End", chapters[1].title)
	}

	@Test
	fun testGetPages_cleansAdsAndScripts() = runBlocking {
		val dummyChapter = MangaChapter(
			id = 1260067L,
			title = "Chapter 1",
			number = 1f,
			volume = 0,
			url = "https://freewebnovel.com/novel/shadow-slave/chapter-1",
			uploadDate = 0L,
			source = NovelParserSource.FREEWEBNOVEL,
			scanlator = null,
			branch = null,
		)

		val pages = repository.getPagesImpl(dummyChapter)
		assertEquals(1, pages.size)
		val content = pages.first().url

		assertTrue("Must contain story content", content.contains("A frail-looking young man with pale skin"))
		assertFalse("Ads must be removed", content.contains("ad-box"))
		assertFalse("Reader ad skips must be removed", content.contains("reader-ad-skip"))
		assertFalse("Comments widget must be removed", content.contains("comments-widget"))
		assertFalse("Scripts must be removed", content.contains("alert("))
	}

	companion object {
		private fun buildCatalogPage1Html(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="li-row">
				<div class="li">
					<div class="con">
						<div class="pic">
							<a href="/novel/shadow-slave">
								<img src="/files/article/image/1/1991/1991s.jpg" />
							</a>
						</div>
						<div class="txt">
							<h3 class="tit"><a href="/novel/shadow-slave" title="Shadow Slave">Shadow Slave</a></h3>
							<div class="core">
								<span>4.6</span>
							</div>
							<div class="desc">
								<div class="item">
									<div class="right"><a href="/genre/Action" class="novel">Action</a></div>
								</div>
							</div>
						</div>
					</div>
				</div>
			</div>
			<div class="li-row">
				<div class="li">
					<div class="con">
						<div class="pic">
							<a href="/novel/invincible-novel">
								<img src="/files/article/image/0/573/573s.jpg" />
							</a>
						</div>
						<div class="txt">
							<div class="tit"><a href="/novel/invincible-novel" title="Invincible">Invincible</a></div>
							<div class="core">
								<span>4.2</span>
							</div>
						</div>
					</div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildCatalogPage2Html(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="li-row">
				<div class="li">
					<div class="con">
						<div class="pic">
							<a href="/novel/the-sovereigns-ascension">
								<img src="/files/article/image/1/2001/2001s.jpg" />
							</a>
						</div>
						<div class="txt">
							<h3 class="tit"><a href="/novel/the-sovereigns-ascension" title="The Sovereign's Ascension">The Sovereign's Ascension</a></h3>
						</div>
					</div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildSearchHtml(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div class="li-row">
				<div class="li">
					<div class="con">
						<div class="pic">
							<a href="/novel/shadow-slave">
								<img src="/files/article/image/1/1991/1991s.jpg" />
							</a>
						</div>
						<div class="txt">
							<h3 class="tit"><a href="/novel/shadow-slave" title="Shadow Slave">Shadow Slave</a></h3>
						</div>
					</div>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildNovelDetailsHtml(): String = """
			<!DOCTYPE html>
			<html>
			<head>
				<meta property="og:novel:author" content="Guiltythree" />
				<meta property="og:novel:genre" content="Action, Adventure, Fantasy" />
				<meta property="og:novel:status" content="OnGoing" />
			</head>
			<body>
			<div id="indexListPage" data-total-chapters="3220">
				<div class="m-info">
					<div class="pic"><img src="/files/article/image/1/1991/1991s.jpg" /></div>
					<div class="inner">
						<p>Growing up in poverty, Sunny was chosen by the Nightmare Spell...</p>
					</div>
					<ul class="ul-list5" id="idData">
						<li><a href="/novel/shadow-slave/chapter-1" title="Chapter 1: Nightmare Begins">Chapter 1: Nightmare Begins</a></li>
					</ul>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildShortNovelDetailsHtml(): String = """
			<!DOCTYPE html>
			<html>
			<head>
				<meta property="og:novel:author" content="ShortAuthor" />
				<meta property="og:novel:status" content="Completed" />
			</head>
			<body>
			<div id="indexListPage" data-total-chapters="2">
				<div class="m-info">
					<div class="inner">A very short novel.</div>
					<ul class="ul-list5" id="idData">
						<li><a href="/novel/short-novel/chapter-1" title="Chapter 1: Short Intro">Chapter 1: Short Intro</a></li>
						<li><a href="/novel/short-novel/chapter-2" title="Chapter 2: Short End">Chapter 2: Short End</a></li>
					</ul>
				</div>
			</div>
			</body>
			</html>
		""".trimIndent()

		private fun buildChaptersPage1Json(): String = """
			{
				"code": 200,
				"page": 1,
				"pageSize": 2,
				"totalPage": 2,
				"totalChapters": 4,
				"html": "<li><a href=\"/novel/shadow-slave/chapter-1\" title=\"Chapter 1: Nightmare Begins\">Chapter 1: Nightmare Begins</a></li><li><a href=\"/novel/shadow-slave/chapter-2\" title=\"Chapter 2: Slave Caravan\">Chapter 2: Slave Caravan</a></li>"
			}
		""".trimIndent()

		private fun buildChaptersPage2Json(): String = """
			{
				"code": 200,
				"page": 2,
				"pageSize": 2,
				"totalPage": 2,
				"totalChapters": 4,
				"html": "<li><a href=\"/novel/shadow-slave/chapter-3\" title=\"Chapter 3: The Strings of Fate\">Chapter 3: The Strings of Fate</a></li><li><a href=\"/novel/shadow-slave/chapter-4\" title=\"Chapter 4: Mountain King\">Chapter 4: Mountain King</a></li>"
			}
		""".trimIndent()

		private fun buildReaderChapterHtml(): String = """
			<!DOCTYPE html>
			<html>
			<body>
			<div id="article">
				<div class="ad-box">Ad Banner</div>
				<div class="reader-ad-skip">Skip ad</div>
				<h4>Chapter 1: Nightmare Begins</h4>
				<p>A frail-looking young man with pale skin was sitting on a rusty bench.</p>
				<div class="comments-widget">Comments section</div>
				<script>alert('eval')</script>
			</div>
			</body>
			</html>
		""".trimIndent()
	}
}
