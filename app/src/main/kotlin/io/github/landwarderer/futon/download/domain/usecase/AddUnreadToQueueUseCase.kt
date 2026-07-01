package io.github.landwarderer.futon.download.domain.usecase

import android.util.Log
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import io.github.landwarderer.futon.mihon.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

class AddUnreadToQueueUseCase @Inject constructor(
    private val db: MangaDatabase,
    private val downloadQueueRepository: DownloadQueueRepository,
) {
    suspend operator fun invoke(manga: Manga, wifiOnly: Boolean, chargingOnly: Boolean, offPeakOnly: Boolean) {
        runCatchingCancellable {
            val history = db.getHistoryDao().find(manga.id)
            val lastChapterId = history?.chapterId ?: -1L
            
            val chapters = db.getChaptersDao().findAll(manga.id)
            val lastChapterIndex = chapters.find { it.chapterId == lastChapterId }?.index ?: -1
            
            val unreadChaptersIds = chapters
                .filter { it.index > lastChapterIndex }
                .map { it.chapterId }
                .toLongArray()

            Log.d("AddUnreadToQueue", "Found ${unreadChaptersIds.size} unread chapters for ${manga.title}")

            if (unreadChaptersIds.isNotEmpty()) {
                downloadQueueRepository.addToQueue(
                    manga = manga,
                    chaptersIds = unreadChaptersIds,
                    wifiOnly = wifiOnly,
                    chargingOnly = chargingOnly,
                    offPeakOnly = offPeakOnly
                )
            }
        }.onFailure {
            it.printStackTraceDebug()
        }
    }
}
