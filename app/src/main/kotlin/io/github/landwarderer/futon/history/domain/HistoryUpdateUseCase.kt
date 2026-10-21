package io.github.landwarderer.futon.history.domain

import android.util.Log
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import io.github.landwarderer.futon.download.domain.usecase.SmartDownloadUseCase
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.domain.DeleteReadChaptersUseCase
import io.github.landwarderer.futon.reader.ui.ReaderState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

class HistoryUpdateUseCase @Inject constructor(
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
	private val db: MangaDatabase,
	private val downloadQueueRepository: DownloadQueueRepository,
	private val deleteReadChaptersUseCase: DeleteReadChaptersUseCase,
	private val localMangaRepository: LocalMangaRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val smartDownloadUseCase: SmartDownloadUseCase,
	private val saveOrder: HistorySaveOrder,
) {

	private var lastCheckedChapterId: Long = -1L

	suspend operator fun invoke(manga: Manga, readerState: ReaderState, percent: Float) {
		save(saveOrder.next(), manga, readerState, percent)
	}

	fun invokeAsync(
		manga: Manga,
		readerState: ReaderState,
		percent: Float
	): Job {
		val sequence = saveOrder.next() // taken now, so the order of requests is kept
		return processLifecycleScope.launch(Dispatchers.IO, CoroutineStart.ATOMIC) {
			runCatchingCancellable {
				withContext(NonCancellable) {
					save(sequence, manga, readerState, percent)
				}
			}.onFailure {
				it.printStackTraceDebug("HistoryUpdateUseCase::invokeAsync")
			}
		}
	}

	private suspend fun save(sequence: Long, manga: Manga, readerState: ReaderState, percent: Float) {
		val isSaved = saveOrder.runIfLatest(manga.id, sequence) {
			historyRepository.addOrUpdate(
				manga = manga,
				chapterId = readerState.chapterId,
				page = readerState.page,
				scroll = readerState.scroll,
				percent = percent,
				force = false,
			)
		}
		if (!isSaved) {
			return // a newer position has already been saved
		}
		if (settings.isAutoDownloadNextChapterEnabled && lastCheckedChapterId != readerState.chapterId) {
			Log.d("SmartDownloads", "Chapter changed, triggering smart download for ${manga.title}")
			lastCheckedChapterId = readerState.chapterId
			smartDownloadUseCase(manga, readerState.chapterId)
		}
	}
}
