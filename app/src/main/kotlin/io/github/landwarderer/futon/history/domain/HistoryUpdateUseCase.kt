package io.github.landwarderer.futon.history.domain

import android.util.Log
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.TriStateOption
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.domain.DeleteReadChaptersUseCase
import io.github.landwarderer.futon.reader.ui.ReaderState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
) {

	private var lastCheckedChapterId: Long = -1L

	suspend operator fun invoke(manga: Manga, readerState: ReaderState, percent: Float) {
		historyRepository.addOrUpdate(
			manga = manga,
			chapterId = readerState.chapterId,
			page = readerState.page,
			scroll = readerState.scroll,
			percent = percent,
			force = false,
		)
		if (settings.isAutoDownloadNextChapterEnabled && lastCheckedChapterId != readerState.chapterId && percent > 0.9f) {
			Log.d("SmartDownloads", "Threshold met (percent=$percent), triggering check for ${manga.title}")
			lastCheckedChapterId = readerState.chapterId
			autoDownloadNext(manga, readerState.chapterId)
		}
	}

	private suspend fun autoDownloadNext(manga: Manga, currentChapterId: Long) {
		runCatchingCancellable {
			Log.d("SmartDownloads", "Checking auto-download for manga: ${manga.title}, chapter: $currentChapterId")
			val chapters = db.getChaptersDao().findAll(manga.id)
			val currentChapter = chapters.find { it.chapterId == currentChapterId } ?: return@runCatchingCancellable
			val branch = currentChapter.branch

			val localManga = localMangaRepository.findSavedManga(manga, withDetails = true)
			val downloadedChapterIds = localManga?.manga?.chapters?.map { it.id }?.toSet() ?: emptySet()

			val nextChapter = chapters
				.filter { it.branch == branch }
				.let { branchChapters ->
					val currentIndexInBranch = branchChapters.indexOf(currentChapter)
					if (currentIndexInBranch != -1 && currentIndexInBranch < branchChapters.size - 1) {
						branchChapters.subList(currentIndexInBranch + 1, branchChapters.size)
							.find { it.chapterId !in downloadedChapterIds }
					} else {
						null
					}
				}

			if (nextChapter == null) {
				Log.d("SmartDownloads", "No next chapter found to download for ${manga.title}")
				return@runCatchingCancellable
			}

			Log.d("SmartDownloads", "Adding next chapter to queue: ${nextChapter.title} (ID: ${nextChapter.chapterId})")
			// Smart Downloads: Next chapter is added to queue, oldest read chapter will be deleted when a chapter download finishes
			val wifiOnly = settings.allowDownloadOnMeteredNetwork == TriStateOption.DISABLED
			downloadQueueRepository.addToQueue(
				manga = manga,
				chaptersIds = longArrayOf(nextChapter.chapterId),
				wifiOnly = wifiOnly,
				chargingOnly = settings.isDownloadOnlyOnChargingEnabled,
				offPeakOnly = settings.isDownloadOffPeakEnabled,
				isPaused = false
			)
		}.onFailure {
			it.printStackTraceDebug("HistoryUpdateUseCase::autoDownloadNext")
		}
	}

	fun invokeAsync(
		manga: Manga,
		readerState: ReaderState,
		percent: Float
	) = processLifecycleScope.launch(Dispatchers.IO, CoroutineStart.ATOMIC) {
		runCatchingCancellable {
			withContext(NonCancellable) {
				invoke(manga, readerState, percent)
			}
		}.onFailure {
			it.printStackTraceDebug("HistoryUpdateUseCase::invokeAsync")
		}
	}
}
