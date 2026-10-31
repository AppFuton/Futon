package io.github.landwarderer.futon.details.domain

import androidx.room.withTransaction
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.model.getPreferredBranch
import io.github.landwarderer.futon.core.model.isLocal
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.history.data.HistoryEntity
import io.github.landwarderer.futon.list.domain.ReadingProgress
import io.github.landwarderer.futon.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

class ProgressUpdateUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val database: MangaDatabase,
	private val localMangaRepository: LocalMangaRepository,
	private val networkState: NetworkState,
) {

	suspend operator fun invoke(manga: Manga): Float {
		val history = database.getHistoryDao().find(manga.id) ?: return PROGRESS_NONE
		val seed = if (manga.isLocal) {
			localMangaRepository.getRemoteManga(manga) ?: manga
		} else {
			manga
		}
		if (!seed.isLocal && !networkState.value) {
			return PROGRESS_NONE
		}
		val repo = mangaRepositoryFactory.create(seed.source)
		val details = if (manga.source != seed.source || seed.chapters.isNullOrEmpty()) {
			repo.getDetails(seed)
		} else {
			seed
		}
		val chapter = details.findChapterById(history.chapterId)
			?: return estimateFromCounts(manga, details, history)
		val chapters = details.getChapters(chapter.branch)
		val chapterRepo = if (repo.source == chapter.source) {
			repo
		} else {
			mangaRepositoryFactory.create(chapter.source)
		}
		val chaptersCount = chapters.size
		if (chaptersCount == 0) {
			return PROGRESS_NONE
		}
		val chapterIndex = chapters.indexOfFirst { x -> x.id == history.chapterId }
		val pagesCount = chapterRepo.getPages(chapter).size
		if (pagesCount == 0) {
			return PROGRESS_NONE
		}
		val pagePercent = (history.page + 1) / pagesCount.toFloat()
		val ppc = 1f / chaptersCount
		val result = ppc * chapterIndex + ppc * pagePercent
		// history.chaptersCount is the chapter count at the reader's last save: smart resume relies on it
		// to tell that the saved chapter was the latest one, so only the reader may change it.
		if (result != history.percent) {
			updateIfUnchanged(
				manga = manga,
				history = history,
				newHistory = history.copy(
					chapterId = chapter.id,
					percent = result,
				),
			)
		}
		return result
	}

	/**
	 * Fallback when the stored history chapter id is no longer present in the fresh details
	 * (e.g. the source rotated chapter URLs, which changes derived ids). We can't pinpoint the
	 * reading position anymore, so we estimate it from the previously stored counts: roughly
	 * `percent * oldTotal` chapters were read, rescaled to the new total. The read position
	 * itself is not touched.
	 *
	 * The rescale may only ever move progress *down*: the stored count and the new total are not
	 * guaranteed to describe the same branch, and with only a few chapters in the new total almost
	 * any stored percent would clamp to 1.0 and mark the manga completed. A guess must never claim
	 * a completion the user never reached.
	 */
	private suspend fun estimateFromCounts(manga: Manga, details: Manga, history: HistoryEntity): Float {
		val newTotal = details.getChapters(details.getPreferredBranch(null)).size
			.takeIf { it > 0 } ?: details.chapters?.size ?: 0
		if (newTotal == 0 || history.chaptersCount <= 0 || !ReadingProgress.isValid(history.percent)) {
			return PROGRESS_NONE
		}
		val estimated = if (ReadingProgress.isCompleted(history.percent)) {
			// Genuinely finished before the ids rotated: stay finished.
			history.percent
		} else {
			// Gaining chapters legitimately lowers progress; losing them may not raise it.
			(history.percent * history.chaptersCount / newTotal).coerceIn(0f, history.percent)
		}
		if (estimated != history.percent || history.chaptersCount != newTotal) {
			updateIfUnchanged(
				manga = manga,
				history = history,
				newHistory = history.copy(percent = estimated, chaptersCount = newTotal),
			)
		}
		return estimated
	}

	private suspend fun updateIfUnchanged(manga: Manga, history: HistoryEntity, newHistory: HistoryEntity) {
		database.withTransaction {
			// Loading metadata may outlive a newer reader save. Never restore that stale snapshot.
			if (database.getHistoryDao().find(manga.id) == history) {
				database.getHistoryDao().update(newHistory)
			}
		}
	}
}
