package io.github.landwarderer.futon.download.domain.usecase

import androidx.work.WorkManager
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.download.ui.worker.DownloadSchedulerWorker
import io.github.landwarderer.futon.favourites.data.toManga
import io.github.landwarderer.futon.mihon.parsers.util.runCatchingCancellable
import javax.inject.Inject

class QueueAllUnreadFromFavoritesUseCase @Inject constructor(
    private val db: MangaDatabase,
    private val addUnreadToQueueUseCase: AddUnreadToQueueUseCase,
    private val workManager: WorkManager,
) {
    suspend operator fun invoke(wifiOnly: Boolean, chargingOnly: Boolean, offPeakOnly: Boolean) {
        runCatchingCancellable {
            val favorites = db.getFavouritesDao().findAll()
            favorites.forEach { favorite ->
                addUnreadToQueueUseCase(
                    manga = favorite.toManga(),
                    wifiOnly = wifiOnly,
                    chargingOnly = chargingOnly,
                    offPeakOnly = offPeakOnly,
                )
            }
            DownloadSchedulerWorker.enqueue(workManager)
        }.onFailure {
            it.printStackTraceDebug()
        }
    }
}
