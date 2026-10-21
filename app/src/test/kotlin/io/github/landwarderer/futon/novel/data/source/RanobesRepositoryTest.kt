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
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.mockito.Mockito.mock

class RanobesRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val url = chain.request().url.toString()
			val responseBody = when {
				url.contains("/ranking") -> buildRankingHtml()
				url.contains("/novels/1207122") -> buildNovelDetailsHtml()
				url.contains("/novels") -> buildNovelsHtml()
				url.contains("/updates") -> buildUpdatesHtml()
				url.contains("/search/sword") || url.contains("story=sword") -> buildSearchHtml()
				url.contains("/chapters/1207122/page/2") -> buildChaptersPage2Html()
				url.contains("/chapters/1207122") -> buildChaptersPage1Html()
				url.contains("/3316269.html") -> buildReaderChapterHtml()
				else -> "<html><body></body></html>"
			}
			Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(responseBody.toResponseBody("text/html; charset=utf-8".toMediaType()))
				.build()
		}
		.build()

	private val cache = mock(MemoryContentCache::class.java)
	private val repository = RanobesRepository(cache, httpClient)

	@Test
	fun testGetList_popularityRanking() = runBlocking {
		val novels = repository.getList(0, SortOrder.POPULARITY, null)
		assertTrue("Popularity ranking should return items", novels.isNotEmpty())

		val first = novels.first()
		assertEquals("Forty-Nine Doomsday Rules", first.title)
		assertTrue("Novel URL must start with https://ranobes.net", first.url.startsWith("https://ranobes.net/novels/"))
		assertFalse("Cover URL must not contain /thumbs/", first.coverUrl?.contains("/thumbs/") == true)
		assertTrue("Cover URL must be full-res webp", first.coverUrl?.endsWith(".webp") == true)
		assertTrue("Description must be extracted", first.description?.isNotBlank() == true)

		// Page 2 of ranking should return empty list (single ranking page)
		val page2 = repository.getList(20, SortOrder.POPULARITY, null)
		assertTrue("Page 2 of ranking should be empty", page2.isEmpty())
	}

	@Test
	fun testGetList_newestNovels() = runBlocking {
		val novels = repository.getList(0, SortOrder.NEWEST, null)
		assertTrue("Catalog should return novels", novels.isNotEmpty())

		val first = novels.first()
		assertEquals("My Strange Life: Role Playing Game", first.title)
		assertTrue("Novel URL must start with https://ranobes.net", first.url.startsWith("https://ranobes.net/novels/"))
		assertFalse("Cover URL must not contain /thumbs/", first.coverUrl?.contains("/thumbs/") == true)
		assertEquals("https://ranobes.net/uploads/posts/2026-09/1789452574_my-strange-life-role-playing-game.webp", first.coverUrl)
	}

	@Test
	fun testGetList_updatesTransformsChapterUrlToNovelUrl() = runBlocking {
		val novels = repository.getList(0, SortOrder.UPDATED, null)
		assertTrue("Updates should return items", novels.isNotEmpty())

		val first = novels.first()
		assertEquals("Forty-Nine Doomsday Rules", first.title)
		// Should transform chapter url /forty-nine-doomsday-rules-1207122/3316269.html to novel url
		assertEquals("https://ranobes.net/novels/1207122-forty-nine-doomsday-rules.html", first.url)
		assertFalse("Cover URL must not contain /thumbs/", first.coverUrl?.contains("/thumbs/") == true)
		assertTrue("Description should contain chapter and release time", first.description?.contains("Chapter 699") == true)
	}

	@Test
	fun testGetList_searchQuery() = runBlocking {
		val filter = MangaListFilter(query = "sword")
		val novels = repository.getList(0, null, filter)
		assertTrue("Search should return novels", novels.isNotEmpty())

		val first = novels.first()
		assertTrue("Search result must match sword query", first.title.contains("Sword", ignoreCase = true))
	}

	@Test
	fun testGetDetails_extractsMetadataAndWindowDataJsonChapters() = runBlocking {
		val dummyManga = Manga(
			id = 1207122L,
			title = "Forty-Nine Doomsday Rules",
			altTitles = emptySet(),
			url = "https://ranobes.net/novels/1207122-forty-nine-doomsday-rules.html",
			publicUrl = "https://ranobes.net/novels/1207122-forty-nine-doomsday-rules.html",
			rating = 0f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet<MangaTag>(),
			state = null,
			authors = emptySet<String>(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = NovelParserSource.RANOBES,
		)

		val detailed = repository.getDetailsImpl(dummyManga)
		assertEquals("Forty-Nine Doomsday Rules", detailed.title)
		assertNotNull("Author should be extracted", detailed.authors)
		assertTrue("Author set should contain AuthorName", detailed.authors?.contains("AuthorName") == true)
		assertTrue("Description must be populated", detailed.description?.isNotBlank() == true)

		val chapters = detailed.chapters
		assertNotNull("Chapters must not be null", chapters)
		assertEquals("Should have all 4 chapters from 2 pages of JSON data", 4, chapters?.size)

		// Chapters should be ordered chronologically: Chapter 1 to Chapter 4
		assertEquals("Chapter 1: The Beginning", chapters!![0].title)
		assertEquals(1f, chapters[0].number)
		assertEquals("Chapter 2: The Awakening", chapters[1].title)
		assertEquals(2f, chapters[1].number)
		assertEquals("Chapter 3: The Threat", chapters[2].title)
		assertEquals(3f, chapters[2].number)
		assertEquals("Chapter 4: The Final Proposal", chapters[3].title)
		assertEquals(4f, chapters[3].number)
	}

	@Test
	fun testGetPages_cleansAdsAndPreservesReaderText() = runBlocking {
		val dummyChapter = MangaChapter(
			id = 3316269L,
			title = "Chapter 4",
			number = 4f,
			volume = 0,
			url = "https://ranobes.net/forty-nine-doomsday-rules-1207122/3316269.html",
			uploadDate = 0L,
			source = NovelParserSource.RANOBES,
			scanlator = null,
			branch = null,
		)

		val pages = repository.getPagesImpl(dummyChapter)
		assertEquals(1, pages.size)
		val html = pages.first().url

		assertTrue("Should contain article text", html.contains("This is the sacred text of the chapter."))
		assertFalse("Ads must be removed", html.contains("Ad content to be removed"))
		assertFalse("Free support banner must be removed", html.contains("free-support-top"))
		assertFalse("Scripts must be removed", html.contains("alert("))
	}

	companion object {
		private fun buildRankingHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div class="content">
					<article class="block story rank-story">
						<div class="title"><a href="https://ranobes.net/novels/1207122-forty-nine-doomsday-rules.html">Forty-Nine Doomsday Rules</a></div>
						<figure class="cover" style="background-image: url('//ranobes.net/uploads/posts/2026-09/thumbs/1207122.webp')"></figure>
						<div class="moreless__short">A gripping survival story in an apocalyptic wasteland.</div>
					</article>
				</div>
				</body>
				</html>
			""".trimIndent()
		}

		private fun buildNovelsHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div class="content">
					<article class="block story shortstory mod-poster">
						<h2 class="title"><a href="https://ranobes.net/novels/1207224-my-strange-life-role-playing-game.html">My Strange Life: Role Playing Game</a></h2>
						<figure class="cover" style="background-image: url('//ranobes.net/uploads/posts/2026-09/thumbs/1789452574_my-strange-life-role-playing-game.webp')"></figure>
						<div class="story">A mysterious RPG system awakens in modern day society.</div>
					</article>
				</div>
				</body>
				</html>
			""".trimIndent()
		}

		private fun buildUpdatesHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div class="content">
					<div class="block story_line story_line-img">
						<a href="/forty-nine-doomsday-rules-1207122/3316269.html" title="Forty-Nine Doomsday Rules">
							<i class="image cover" style="background-image:url('https://ranobes.net/uploads/posts/2026-09/thumbs/1207122.webp')"></i>
							<h3 class="title small ellipses">Forty-Nine Doomsday Rules</h3>
							<span class="subtitle ellipses">Chapter 699: The Final Trade Proposal</span>
							<em class="small grey">11 minutes ago</em>
						</a>
					</div>
				</div>
				</body>
				</html>
			""".trimIndent()
		}

		private fun buildSearchHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div class="content">
					<article class="block story shortstory">
						<h2 class="title"><a href="https://ranobes.net/novels/9999-sword-god.html">Sword God of the Heavens</a></h2>
						<figure class="cover" style="background-image: url('https://ranobes.net/uploads/posts/2026-09/thumbs/sword.webp')"></figure>
						<div class="story">A young swordsman climbs the celestial ladder.</div>
					</article>
				</div>
				</body>
				</html>
			""".trimIndent()
		}

		private fun buildNovelDetailsHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div class="r-fullstory">
					<div class="r-fullstory-poster">
						<figure class="cover" style="background-image: url('https://ranobes.net/uploads/posts/2026-09/thumbs/1207122.webp')"></figure>
					</div>
					<h1 class="title">Forty-Nine Doomsday Rules</h1>
					<div class="moreless__full">Full detailed synopsis of Forty-Nine Doomsday Rules.</div>
					<div class="meta">
						<a href="https://ranobes.net/authors/AuthorName/">AuthorName</a>
						<a href="https://ranobes.net/tags/Action/">Action</a>
					</div>
					<div class="chapters-btn">
						<a href="https://ranobes.net/chapters/1207122/">Table of Contents</a>
					</div>
				</div>
				</body>
				</html>
			""".trimIndent()
		}

		private fun buildChaptersPage1Html(): String {
			return """
				<!DOCTYPE html>
				<html>
				<head>
				<script>
				window.__DATA__ = {
					"book_id": 1207122,
					"pages_count": 2,
					"chapters": [
						{"id": 4, "title": "Chapter 4: The Final Proposal", "link": "https://ranobes.net/forty-nine-doomsday-rules-1207122/3316269.html", "date": 1729424700},
						{"id": 3, "title": "Chapter 3: The Threat", "link": "https://ranobes.net/forty-nine-doomsday-rules-1207122/3316268.html", "date": 1729424600}
					]
				};
				</script>
				</head>
				<body></body>
				</html>
			""".trimIndent()
		}

		private fun buildChaptersPage2Html(): String {
			return """
				<!DOCTYPE html>
				<html>
				<head>
				<script>
				window.__DATA__ = {
					"book_id": 1207122,
					"pages_count": 2,
					"chapters": [
						{"id": 2, "title": "Chapter 2: The Awakening", "link": "https://ranobes.net/forty-nine-doomsday-rules-1207122/3316267.html", "date": 1729424500},
						{"id": 1, "title": "Chapter 1: The Beginning", "link": "https://ranobes.net/forty-nine-doomsday-rules-1207122/3316266.html", "date": 1729424400}
					]
				};
				</script>
				</head>
				<body></body>
				</html>
			""".trimIndent()
		}

		private fun buildReaderChapterHtml(): String {
			return """
				<!DOCTYPE html>
				<html>
				<body>
				<div id="arrticle">
					<div class="free-support-top">Support Ranobes banner</div>
					<div class="ads">Ad content to be removed</div>
					<p>This is the sacred text of the chapter.</p>
					<script>alert('malicious')</script>
				</div>
				</body>
				</html>
			""".trimIndent()
		}
	}
}
