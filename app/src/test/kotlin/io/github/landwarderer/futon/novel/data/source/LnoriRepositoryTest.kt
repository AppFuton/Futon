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
import org.junit.Before
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class LnoriRepositoryTest {

	private val httpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val url = chain.request().url.toString()
			val html = when {
				url.contains("/library") -> buildLibraryHtml()
				url.contains("/series/") -> buildSeriesHtml()
				url.contains("/book/") -> buildBookHtml()
				else -> "<html><body></body></html>"
			}
			Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
				.build()
		}
		.build()

	private val cache = mock(MemoryContentCache::class.java)
	private val repository = LnoriRepository(cache, httpClient)

	@Before
	fun setup() {
		// Reset catalog cache if needed
	}

	@Test
	fun testGetList_returnsCatalogNovelsWithMetadata() = runBlocking {
		val novels = repository.getList(0, null, null)
		assertTrue("Catalog should return at least 20 novels", novels.size >= 20)

		for (novel in novels) {
			assertTrue("Novel title must be non-blank", novel.title.isNotBlank())
			assertTrue("Novel url must start with https://lnori.com/series/", novel.url.startsWith("https://lnori.com/series/"))
			assertTrue("Novel cover url must be non-blank", novel.coverUrl?.isNotBlank() == true)
			assertTrue("Novel description must be non-blank", novel.description?.isNotBlank() == true)
			assertFalse("Novel author must not be blank if present", novel.author?.isBlank() == true)
			assertTrue("Novel tags should not be empty", novel.tags.isNotEmpty())
		}
	}

	@Test
	fun testGetList_queryFilterFindsMatchingNovels() = runBlocking {
		val filter = MangaListFilter(query = "Re:ZERO")
		val results = repository.getList(0, null, filter)

		assertTrue("Should find results for query 'Re:ZERO'", results.isNotEmpty())
		for (novel in results) {
			val matchesTitle = novel.title.contains("Re:ZERO", ignoreCase = true)
			val matchesAuthor = novel.author?.contains("Re:ZERO", ignoreCase = true) == true
			assertTrue("Result must match query in title or author", matchesTitle || matchesAuthor)
		}
	}

	@Test
	fun testGetList_tagFilterIncludeAndExclude() = runBlocking {
		val isekaiTag = MangaTag(title = "Isekai", key = "isekai", source = NovelParserSource.LNORI)
		val filterInclude = MangaListFilter(tags = setOf(isekaiTag))
		val isekaiNovels = repository.getList(0, null, filterInclude)

		assertTrue("Should find isekai novels", isekaiNovels.isNotEmpty())
		for (novel in isekaiNovels) {
			assertTrue("Novel must have isekai tag", novel.tags.any { it.key.equals("isekai", ignoreCase = true) })
		}

		val filterExclude = MangaListFilter(tags = setOf(isekaiTag), tagsExclude = setOf(isekaiTag))
		val excludedNovels = repository.getList(0, null, filterExclude)
		assertTrue("Novels with excluded tag should be empty", excludedNovels.isEmpty())
	}

	@Test
	fun testGetList_alphabeticalSorting() = runBlocking {
		val novels = repository.getList(0, SortOrder.ALPHABETICAL, null)
		assertTrue("Novels list should not be empty", novels.size >= 2)

		for (i in 0 until novels.size - 1) {
			val current = novels[i].title.lowercase()
			val next = novels[i + 1].title.lowercase()
			assertTrue("Catalog must be sorted alphabetically: '$current' <= '$next'", current <= next)
		}
	}

	@Test
	fun testGetFilterOptions_providesAllUniqueTags() = runBlocking {
		val options = repository.getFilterOptions()
		val tags = options.availableTags

		assertTrue("Available tags count should be greater than 100", tags.size >= 100)
		assertTrue("Tags should contain 'isekai'", tags.any { it.key == "isekai" })
		assertTrue("Tags should contain 'action'", tags.any { it.key == "action" })
		assertTrue("Tags should contain 'comedy'", tags.any { it.key == "comedy" })
	}

	@Test
	fun testGetDetails_populatesChaptersWithVolumeLinks() = runBlocking {
		val novels = repository.getList(0, null, null)
		assertTrue("Novels list should not be empty", novels.isNotEmpty())

		val firstNovel = novels.first()
		val detailed = repository.getDetailsImpl(firstNovel)

		assertTrue("Detailed novel title should be non-blank", detailed.title.isNotBlank())
		assertTrue("Detailed novel description should be non-blank", detailed.description?.isNotBlank() == true)

		val chapters = detailed.chapters
		assertNotNull("Detailed novel chapters must not be null", chapters)
		assertTrue("Detailed novel must have at least one chapter", chapters?.isNotEmpty() == true)

		for (chap in chapters!!) {
			assertTrue("Chapter title must be non-blank", chap.title?.isNotBlank() == true)
			assertTrue("Chapter url must contain /book/", chap.url.contains("/book/"))
			assertTrue("Chapter number must be >= 1", chap.number >= 1f)
			assertTrue("Chapter volume must be >= 1", chap.volume >= 1)
		}
	}

	@Test
	fun testGetPages_extractsContentAndRewritesImagesToAvif() = runBlocking {
		val novels = repository.getList(0, null, null)
		val firstNovel = novels.first()
		val detailed = repository.getDetailsImpl(firstNovel)
		val firstChapter = detailed.chapters!!.first()

		val pages = repository.getPagesImpl(firstChapter)
		assertEquals("Novel volume chapter should return exactly 1 page containing reader HTML", 1, pages.size)

		val htmlContent = pages.first().url
		assertTrue("Volume content HTML must be non-blank", htmlContent.isNotBlank())
		assertTrue("Volume content HTML must contain text content", htmlContent.length > 500)

		// Assert that img.lnori.com JPG URLs were rewritten to AVIF without regex null artifacts
		assertFalse("Volume content must not contain img.lnori.com .jpg image references", htmlContent.contains("img.lnori.com") && htmlContent.contains(".jpg"))
		assertFalse("Volume content must not contain .avifnull references", htmlContent.contains(".avifnull"))
	}

	companion object {
		private val TAG_POOL = (1..110).map { "tag$it" } + listOf("isekai", "action", "comedy", "fantasy", "drama", "adventure", "romance")

		private fun buildLibraryHtml(): String {
			val sb = StringBuilder("<!DOCTYPE html><html><body>")
			val novels = listOf(
				"A Certain Magical Index" to "Kamachi Kazuma",
				"Baccano!" to "Narita Ryohgo",
				"Classroom of the Elite" to "Kinugasa Syougo",
				"Date A Live" to "Tachibana Koushi",
				"Eighty-Six" to "Asato Asato",
				"Fate/Zero" to "Urobuchi Gen",
				"Goblin Slayer" to "Kagyu Kumo",
				"Hyouka" to "Yonezawa Honobu",
				"Infinite Dendrogram" to "Kaidou Sakon",
				"Jaku-Chara Tomozaki-kun" to "Yaku Yuuki",
				"KonoSuba" to "Akatsuki Natsume",
				"Log Horizon" to "Touno Mamare",
				"Mushoku Tensei" to "Rifujin na Magonote",
				"No Game No Life" to "Kamiya Yuu",
				"Overlord" to "Maruyama Kugane",
				"Pending Novel" to "Author P",
				"Qualidea Code" to "Sagara Sou",
				"Re:ZERO -Starting Life in Another World-" to "Nagatsuki Tappei",
				"Sword Art Online" to "Kawahara Reki",
				"The Eminence in Shadow" to "Aizawa Daisuke",
				"Utsuro no Hako to Zero no Maria" to "Mikage Eiji",
				"Violet Evergarden" to "Akatsuki Kana",
				"WorldEnd" to "Kareno Akira",
				"Youjo Senki" to "Carlo Zen",
				"Zaregoto Series" to "Nisio Isin",
			)

			novels.forEachIndexed { idx, (title, author) ->
				val startTagIdx = (idx * 4) % TAG_POOL.size
				val tags = mutableSetOf<String>()
				if (title.contains("Re:ZERO")) {
					tags.addAll(listOf("isekai", "action", "comedy", "drama", "fantasy", "adventure"))
				} else if (idx % 2 == 0) {
					tags.add("isekai")
				}
				for (i in 0 until 5) {
					tags.add(TAG_POOL[(startTagIdx + i) % TAG_POOL.size])
				}
				val tagsAttr = tags.joinToString(",")
				val slug = title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
				sb.append("""
					<article class="card" data-id="${1000 + idx}" data-t="$title" data-a="$author" data-d="2020" data-v="10" data-tags="$tagsAttr" data-rel="${1000 - idx}">
						<figure class="card-cover">
							<a href="/series/${1000 + idx}/$slug" class="stretched-link">
								<img src="https://cdn.lnori.com/cover/${1000 + idx}.webp" alt="$title" />
							</a>
						</figure>
						<aside class="card-popup">
							<header class="popup-header">
								<h3 class="popup-title"><a href="/series/${1000 + idx}/$slug">$title</a></h3>
								<p class="popup-author">$author (2020)</p>
							</header>
							<p class="popup-description">This is the description for $title written by $author.</p>
						</aside>
						<h2 class="card-title"><span>$title</span></h2>
					</article>
				""".trimIndent())
			}
			sb.append("</body></html>")
			return sb.toString()
		}

		private fun buildSeriesHtml(): String {
			return """
				<!DOCTYPE html><html><head>
				<script type="application/ld+json">
				{
					"@context": "https://schema.org",
					"@type": "Book",
					"name": "Re:ZERO -Starting Life in Another World-",
					"hasPart": [
						{
							"@type": "Book",
							"name": "Volume 1",
							"position": "1",
							"url": "https://lnori.com/book/3343/1"
						},
						{
							"@type": "Book",
							"name": "Volume 2",
							"position": "2",
							"url": "https://lnori.com/book/3343/2"
						}
					]
				}
				</script>
				</head><body>
				<h1 class="series-title">Re:ZERO -Starting Life in Another World-</h1>
				<div class="series-author"><a href="#">Nagatsuki Tappei</a></div>
				<p class="series-synopsis">Subaru Natsuki is summoned to another fantasy world.</p>
				<figure class="series-cover"><img src="https://cdn.lnori.com/cover/12040.webp" /></figure>
				</body></html>
			""".trimIndent()
		}

		private fun buildBookHtml(): String {
			return """
				<!DOCTYPE html><html><head><title>Volume 1</title></head><body>
				<article class="content-body">
				<h1>Prologue: The Waste Heat of the Beginning</h1>
				<p>Subaru Natsuki was an ordinary high school student who found himself suddenly summoned to an alternate fantasy world with no explanation whatsoever. There was no summoner in sight, nor any grand quest thrust upon him. Just a bustling marketplace full of demi-humans, lizard folk, and magic carriages moving about. Thinking he had obtained typical protagonist powers, he quickly learned that his only ability was to return by death.</p>
				<p>He walked around the capital city trying to make sense of his surroundings, checking his belongings which consisted only of a convenience store plastic bag containing instant ramen, chips, and his uncharged flip phone.</p>
				<img src="https://img.lnori.com/illustrations/3343/1/color01.jpg?token=test" alt="Color Illustration 1" />
				<picture><source srcset="https://img.lnori.com/illustrations/3343/1/color02.jpg" /></picture>
				<p>As dusk fell over the capital city of Lugnica, dark shadows began to loom across the cobblestone alleys...</p>
				</article>
				</body></html>
			""".trimIndent()
		}
	}
}
