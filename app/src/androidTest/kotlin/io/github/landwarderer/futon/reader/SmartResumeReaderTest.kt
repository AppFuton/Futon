package io.github.landwarderer.futon.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.SampleData
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.cache.SafeDeferred
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.nav.ReaderIntent
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.ReaderMode
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.reader.ui.ReaderActivity
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.ReaderViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.io.File
import javax.inject.Inject

/** Exercise the actual Activity/ViewModel/history path with cached metadata and local test images. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SmartResumeReaderTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var cache: MemoryContentCache
    @Inject lateinit var data: MangaDataRepository
    @Inject lateinit var history: HistoryRepository
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun setUp() {
        initializeReaderTestWorkManager(context)
        hiltRule.inject()
        cache.clear(TestMangaSource)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(AppSettings.KEY_SMART_RESUME, true)
            .putString(AppSettings.KEY_SMART_RESUME_MODE, "PERCENTAGE")
            .putString(AppSettings.KEY_SMART_RESUME_PERCENTAGE, "90")
            .putBoolean(AppSettings.KEY_SMART_RESUME_WAIT, false)
            .commit()
    }

    @Test
    fun cachedResumeInStandardAndWebtoonReaders() = runBlocking {
        for (mode in listOf(ReaderMode.STANDARD, ReaderMode.WEBTOON)) {
            val fixture = fixture(mode, cachedSuccessor = true)
            launch(fixture).use { scenario ->
                val model = model(scenario)
                awaitChapter(model, 30)
                if (mode == ReaderMode.STANDARD) assertEquals(0, model.readingState.value?.page)
                assertEquals(mode, model.readerMode.value)
                assertEquals(30L, history.getOne(fixture.updated)?.chapterId)
                delay(1000)
                screenshot("smart-resume-${mode.name.lowercase()}")
            }
        }
    }

    @Test
    fun cachedModeDoesNotJumpWhenDetailsArriveLater() = runBlocking {
        val fixture = fixture(ReaderMode.STANDARD, cachedSuccessor = false, deferUpdate = true)
        launch(fixture).use { scenario ->
            val model = model(scenario)
            awaitChapter(model, 29)
            fixture.update.complete(Result.success(fixture.updated))
            withTimeout(30_000) { model.mangaDetails.first { it?.isLoaded == true } }
            assertEquals(29L, model.readingState.value?.chapterId)
        }
    }

    @Test
    fun waitingDefersContentAndHistoryUntilUpdate() = runBlocking {
        setWaiting()
        val fixture = fixture(ReaderMode.STANDARD, cachedSuccessor = false, deferUpdate = true)
        launch(fixture).use { scenario ->
            val model = model(scenario)
            withTimeout(30_000) { model.readerMode.first { it != null } }
            assertNull(model.readingState.value)
            assertTrue(model.content.value.pages.isEmpty())
            assertEquals(29, history.getOne(fixture.saved)?.chaptersCount)
            fixture.update.complete(Result.success(fixture.updated))
            awaitChapter(model, 30)
            assertEquals(30L, history.getOne(fixture.updated)?.chapterId)
        }
    }

    @Test
    fun failedUpdateReleasesSavedChapter() = runBlocking {
        setWaiting()
        val fixture = fixture(ReaderMode.STANDARD, cachedSuccessor = false, deferUpdate = true)
        launch(fixture).use { scenario ->
            val model = model(scenario)
            withTimeout(30_000) { model.readerMode.first { it != null } }
            fixture.update.complete(Result.failure(IllegalStateException("Test update failed")))
            awaitChapter(model, 29)
            assertEquals(13, model.readingState.value?.page)
        }
    }

    @Test
    fun explicitPositionAndActivityRecreationStayExact() = runBlocking {
        val fixture = fixture(ReaderMode.STANDARD, cachedSuccessor = true)
        launch(fixture, ReaderState(29, 13, 0)).use { scenario ->
            awaitChapter(model(scenario), 29)
            scenario.recreate()
            val model = model(scenario)
            awaitChapter(model, 29)
            assertEquals(13, model.readingState.value?.page)
        }
    }

    @Test
    fun incognitoResumeDoesNotWriteHistory() = runBlocking {
        val fixture = fixture(ReaderMode.STANDARD, cachedSuccessor = true)
        launch(fixture, incognito = true).use { scenario ->
            awaitChapter(model(scenario), 30)
            assertEquals(29L, history.getOne(fixture.updated)?.chapterId)
        }
    }

    private fun setWaiting() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(AppSettings.KEY_SMART_RESUME_WAIT, true).commit()
    }

    private suspend fun fixture(mode: ReaderMode, cachedSuccessor: Boolean, deferUpdate: Boolean = false): Fixture {
        val id = -System.nanoTime()
        val chapters = (1L..30L).map { chapterId ->
            MangaChapter(
                id = chapterId, title = "Chapter $chapterId", number = chapterId.toFloat(), volume = 0,
                url = "https://example.invalid/$id/$chapterId", uploadDate = 0,
                scanlator = null, branch = null, source = TestMangaSource,
            )
        }
        // Include the previous chapter for normal backward metadata loading near the boundary.
        for (chapter in chapters.takeLast(3)) {
            val pages = (1..15).map { pageNumber ->
                val image = File(context.cacheDir, "reader-test-${chapter.id}-$pageNumber.png")
                val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(if (chapter.id == 30L) Color.rgb(205, 235, 220) else Color.rgb(220, 225, 245))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 38f }
                canvas.drawText("Chapter ${chapter.id} / Page $pageNumber", 30f, 100f, paint)
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                MangaPage(chapter.id * 100 + pageNumber, image.toURI().toString(), null, LocalMangaSource)
            }
            cache.putPages(TestMangaSource, chapter.url, SafeDeferred(CompletableDeferred(Result.success(pages))))
        }
        val updated = SampleData.mangaDetails.copy(
            id = id, title = "Reader resume test", source = TestMangaSource,
            url = "https://example.invalid/$id", publicUrl = "https://example.invalid/$id",
            chapters = chapters, tags = emptySet(), contentRating = ContentRating.SAFE,
            description = null, coverUrl = "", largeCoverUrl = null,
        )
        val saved = updated.copy(chapters = chapters.take(29))
        history.addOrUpdate(saved, 29, 13, 0, 1f, force = true)
        data.saveReaderMode(saved, mode)
        if (cachedSuccessor) data.storeManga(updated, replaceExisting = true)
        val update = CompletableDeferred<Result<Manga>>()
        if (!deferUpdate) update.complete(Result.success(updated))
        cache.putDetails(TestMangaSource, updated.url, SafeDeferred(update))
        return Fixture(saved, updated, update)
    }

    private fun launch(fixture: Fixture, state: ReaderState? = null, incognito: Boolean = false): ActivityScenario<ReaderActivity> {
        val builder = ReaderIntent.Builder(context).mangaId(fixture.saved.id)
        if (state != null) builder.state(state)
        if (incognito) builder.incognito()
        return ActivityScenario.launch(builder.build().intent)
    }

    private fun model(scenario: ActivityScenario<ReaderActivity>): ReaderViewModel {
        lateinit var model: ReaderViewModel
        scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        return model
    }

    private suspend fun awaitChapter(model: ReaderViewModel, id: Long) {
        withTimeout(30_000) {
            combine(model.content, model.readingState) { content, state ->
                state?.chapterId == id && content.pages.any { it.chapterId == id }
            }.first { it }
        }
    }

    private fun screenshot(name: String) {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    private data class Fixture(val saved: Manga, val updated: Manga, val update: CompletableDeferred<Result<Manga>>)
}
