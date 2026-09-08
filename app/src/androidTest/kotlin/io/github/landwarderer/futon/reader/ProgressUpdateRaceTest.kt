package io.github.landwarderer.futon.reader

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.SampleData
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.cache.SafeDeferred
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.details.domain.ProgressUpdateUseCase
import io.github.landwarderer.futon.history.data.HistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import javax.inject.Inject

/** Hold page metadata after the history read to reproduce a slow exit-time recalculation deterministically. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProgressUpdateRaceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var cache: MemoryContentCache
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var database: MangaDatabase
    @Inject lateinit var recalculate: ProgressUpdateUseCase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeReaderTestWorkManager(context)
        hiltRule.inject()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(AppSettings.KEY_OFFLINE_DISABLED, true).commit()
    }

    @Test
    fun slowRecalculationCannotOverwriteNewlySavedFinalPage() = runBlocking {
        val fixture = fixture()
        val job = async(Dispatchers.IO) { recalculate(fixture.manga) }
        withTimeout(30_000) { fixture.started.await() }
        history.addOrUpdate(fixture.manga, 30, 19, 73, 1f, force = true)
        val saved = database.getHistoryDao().find(fixture.manga.id)
        fixture.release()
        withTimeout(30_000) { job.await() }
        assertEquals(saved, database.getHistoryDao().find(fixture.manga.id))
    }

    @Test
    fun unchangedHistoryStillGetsCorrected() = runBlocking {
        val fixture = fixture()
        val job = async(Dispatchers.IO) { recalculate(fixture.manga) }
        withTimeout(30_000) { fixture.started.await() }
        fixture.release()
        val result = withTimeout(30_000) { job.await() }
        val saved = database.getHistoryDao().find(fixture.manga.id)!!
        assertEquals(18, saved.page)
        assertEquals(1f - 1f / 600f, result, 0.000001f)
        assertEquals(result, saved.percent)
    }

    @Test
    fun historyRemovedDuringRecalculationStaysRemoved() = runBlocking {
        val fixture = fixture()
        val job = async(Dispatchers.IO) { recalculate(fixture.manga) }
        withTimeout(30_000) { fixture.started.await() }
        database.getHistoryDao().delete(fixture.manga.id)
        fixture.release()
        withTimeout(30_000) { job.await() }
        assertNull(database.getHistoryDao().find(fixture.manga.id))
    }

    private suspend fun fixture(): Fixture {
        val id = -System.nanoTime()
        val chapters = (1L..30L).map {
            MangaChapter(id = it, title = "Chapter $it", number = it.toFloat(), volume = 0,
                url = "https://example.invalid/$id/$it", uploadDate = 0,
                scanlator = null, branch = null, source = TestMangaSource)
        }
        val manga = SampleData.mangaDetails.copy(id = id, source = TestMangaSource,
            url = "https://example.invalid/$id", chapters = chapters)
        history.addOrUpdate(manga, 30, 18, 0, 0.5f, force = true)
        val started = CompletableDeferred<Unit>()
        val pages = CompletableDeferred<Result<List<MangaPage>>>()
        val observed = object : Deferred<Result<List<MangaPage>>> by pages {
            override suspend fun await(): Result<List<MangaPage>> {
                started.complete(Unit)
                return pages.await()
            }
        }
        cache.putPages(TestMangaSource, chapters.last().url, SafeDeferred(observed))
        return Fixture(manga, started, pages)
    }

    private data class Fixture(
        val manga: Manga,
        val started: CompletableDeferred<Unit>,
        val pages: CompletableDeferred<Result<List<MangaPage>>>,
    ) {
        fun release() {
            pages.complete(Result.success((1..20).map {
                MangaPage(it.toLong(), "https://example.invalid/page/$it", null, TestMangaSource)
            }))
        }
    }
}
