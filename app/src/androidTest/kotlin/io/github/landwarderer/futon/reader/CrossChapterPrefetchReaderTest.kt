package io.github.landwarderer.futon.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.SampleData
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.cache.SafeDeferred
import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.nav.ReaderIntent
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.ReaderMode
import io.github.landwarderer.futon.reader.ui.ReaderActivity
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.ReaderViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.concurrent.thread

/** Delay adjacent metadata until the old-page callback has run, then observe real image requests. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CrossChapterPrefetchReaderTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var cache: MemoryContentCache
    @Inject lateinit var data: MangaDataRepository
    // Production initializes networking off the main thread; HiltTestApplication needs the same warm-up.
    @Inject @MangaHttpClient lateinit var httpClient: OkHttpClient
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        hiltRule.inject()
    }

    @Test
    fun prefetchesAfterAsyncAppendInStandardAndWebtoonReaders() = runBlocking {
        checkPrefetch(ReaderMode.STANDARD, enabled = true)
        checkPrefetch(ReaderMode.WEBTOON, enabled = true)
    }

    @Test
    fun disabledPreloadingStillAppendsMetadataWithoutPrefetchingImages() = runBlocking {
        checkPrefetch(ReaderMode.STANDARD, enabled = false)
    }

    private suspend fun checkPrefetch(mode: ReaderMode, enabled: Boolean) {
        cache.clear(TestMangaSource)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(AppSettings.KEY_PAGES_PRELOAD, if (enabled) "1" else "0")
            .putBoolean(AppSettings.KEY_WEBTOON_PULL_GESTURE, false)
            .commit()
        PageServer().use { server ->
            val id = -System.nanoTime()
            val chapters = (29L..30L).map { chapterId ->
                MangaChapter(
                    id = chapterId, title = "Chapter $chapterId", number = chapterId.toFloat(), volume = 0,
                    url = "https://example.invalid/$id/$chapterId", uploadDate = 0,
                    scanlator = null, branch = null, source = TestMangaSource,
                )
            }
            fun pages(chapter: Long) = (1..15).map { number ->
                // MangaDex uses the default direct page URL resolver; all traffic stays on loopback.
                MangaPage(chapter * 100 + number, "${server.url}/$chapter/$number", null, MangaParserSource.MANGADEX)
            }
            val nextPages = CompletableDeferred<Result<List<MangaPage>>>()
            cache.putPages(TestMangaSource, chapters[0].url, SafeDeferred(CompletableDeferred(Result.success(pages(29)))))
            cache.putPages(TestMangaSource, chapters[1].url, SafeDeferred(nextPages))
            val manga = SampleData.mangaDetails.copy(
                id = id, title = "Cross-chapter prefetch test", source = TestMangaSource,
                url = "https://example.invalid/$id", publicUrl = "https://example.invalid/$id",
                chapters = chapters, tags = emptySet(), contentRating = ContentRating.SAFE,
                description = null, coverUrl = "", largeCoverUrl = null,
            )
            cache.putDetails(TestMangaSource, manga.url, SafeDeferred(CompletableDeferred(Result.success(manga))))
            data.saveReaderMode(manga, mode)
            val intent = ReaderIntent.Builder(context).mangaId(id).state(ReaderState(29, 14, 0)).build().intent
            ActivityScenario.launch<ReaderActivity>(intent).use { scenario ->
                lateinit var model: ReaderViewModel
                scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
                withTimeout(30_000) { model.content.first { it.pages.isNotEmpty() } }
                awaitRequest(server, "/29/15", mode)
                assertFalse(model.content.value.pages.any { it.chapterId == 30L })
                nextPages.complete(Result.success(pages(30)))
                withTimeout(30_000) { model.content.first { content -> content.pages.any { it.chapterId == 30L } } }
                if (enabled) {
                    // PageLoader retains its existing six-item queue cap after the reader offers up to ten.
                    awaitRequest(server, "/30/6", mode)
                } else {
                    delay(1500)
                    assertFalse("/30/6" in server.requests)
                }
                assertEquals(29L, model.readingState.value?.chapterId)
            }
        }
    }

    private suspend fun awaitRequest(server: PageServer, path: String, mode: ReaderMode) {
        try {
            withTimeout(30_000) { while (path !in server.requests) delay(50) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("$mode expected $path; received ${server.requests}", e)
        }
    }

    private class PageServer : Closeable {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}"
        val requests = ConcurrentHashMap.newKeySet<String>()
        private val png = ByteArrayOutputStream().also { stream ->
            val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(205, 225, 235))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
        }.toByteArray()
        private val worker = thread(name = "reader-test-pages", isDaemon = true) {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val output = client.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${png.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(png)
                        output.flush()
                        requests += path
                    }
                } catch (e: IOException) {
                    if (!socket.isClosed) throw e
                }
            }
        }

        override fun close() {
            socket.close()
            worker.join(1000)
        }
    }
}
