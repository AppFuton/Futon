package io.github.landwarderer.futon.download.data.repository

import android.util.Log
import androidx.work.WorkManager
import io.github.landwarderer.futon.core.db.dao.MangaDao
import io.github.landwarderer.futon.core.db.entity.toEntity
import io.github.landwarderer.futon.download.data.dao.DownloadQueueDao
import io.github.landwarderer.futon.download.data.entity.DownloadQueueEntity
import io.github.landwarderer.futon.download.ui.worker.DownloadSchedulerWorker
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueueRepository @Inject constructor(
    private val downloadQueueDao: DownloadQueueDao,
    private val mangaDao: MangaDao,
    private val workManager: WorkManager,
) {
    fun observeQueue(): Flow<List<DownloadQueueEntity>> = downloadQueueDao.observeAll()

    suspend fun getQueue(): List<DownloadQueueEntity> = downloadQueueDao.getAll()

    suspend fun addToQueue(
        manga: Manga,
        chaptersIds: LongArray,
        wifiOnly: Boolean = true,
        chargingOnly: Boolean = false,
        offPeakOnly: Boolean = false,
        isPaused: Boolean = false
    ) {
        Log.d("DownloadQueue", "Adding ${chaptersIds.size} chapters to queue for ${manga.title}. WifiOnly: $wifiOnly, ChargingOnly: $chargingOnly, OffPeakOnly: $offPeakOnly, Paused: $isPaused")
        val currentQueue = downloadQueueDao.getAll()
        val existingForManga = currentQueue.filter { it.mangaId == manga.id }
        val newChapters = chaptersIds.filter { id ->
            existingForManga.none { it.chaptersIds.contains(id) }
        }.toLongArray()

        if (newChapters.isEmpty()) {
            Log.d("DownloadQueue", "All requested chapters are already in queue for ${manga.title}")
            return
        }

        mangaDao.upsert(manga.toEntity())
        val maxPriority = currentQueue.maxOfOrNull { it.priority } ?: -1
        val entity = DownloadQueueEntity(
            mangaId = manga.id,
            chaptersIds = newChapters,
            priority = maxPriority + 1,
            wifiOnly = wifiOnly,
            charging_only = chargingOnly,
            offPeakOnly = offPeakOnly,
            isPaused = isPaused
        )
        downloadQueueDao.insert(entity)
        Log.d("DownloadQueue", "Successfully added ${newChapters.size} chapters to queue for ${manga.title}. Enqueuing DownloadSchedulerWorker with 500ms delay.")
        DownloadSchedulerWorker.enqueue(workManager, delay = 500)
    }

    suspend fun updatePaused(id: Long, isPaused: Boolean) {
        val entity = downloadQueueDao.getAll().find { it.id == id } ?: return
        downloadQueueDao.update(entity.copy(isPaused = isPaused))
        if (!isPaused) {
            DownloadSchedulerWorker.enqueue(workManager, force = true)
        }
    }

    suspend fun removeFromQueue(id: Long) {
        downloadQueueDao.delete(id)
    }

    suspend fun updateQueueOrder(ids: List<Long>) {
        downloadQueueDao.reorder(ids)
    }

    suspend fun clearQueue() {
        downloadQueueDao.deleteAll()
    }
}
