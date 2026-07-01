package io.github.landwarderer.futon.download.ui.worker

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.awaitWorkInfosByTag
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class DownloadSchedulerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val mangaDataRepository: MangaDataRepository,
    private val settings: AppSettings,
    private val workManager: WorkManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("DownloadScheduler", "DownloadSchedulerWorker starting")
        val force = inputData.getBoolean(KEY_FORCE, false)
        return runCatchingCancellable {
            val activeWorks = workManager.awaitWorkInfosByTag(DownloadWorker.TAG)
                .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
            
            Log.d("DownloadScheduler", "Active works: ${activeWorks.map { it.id to it.state }}")

            if (activeWorks.any { it.state == WorkInfo.State.RUNNING }) {
                Log.d("DownloadScheduler", "A download is already running, scheduling check in 30s")
                scheduleNextCheck(30, TimeUnit.SECONDS)
                return@runCatchingCancellable Result.success()
            }

            val queue = downloadQueueRepository.getQueue()
            Log.d("DownloadScheduler", "Queue size: ${queue.size}, force=$force")
            if (queue.isEmpty()) {
                return@runCatchingCancellable Result.success()
            }

            val nextItem = queue.firstOrNull { 
                if (it.isPaused && !force) return@firstOrNull false
                if (force) return@firstOrNull true

                val requiresOffPeak = it.offPeakOnly
                val requiresCharging = it.charging_only
                val requiresWifi = it.wifiOnly
                
                val canRunOffPeak = !requiresOffPeak || isOffPeakTime()
                val canRunCharging = !requiresCharging || isCharging()
                val canRunWifi = !requiresWifi || !isMetered()
                
                Log.d("DownloadScheduler", "Checking item ${it.id} for manga ${it.mangaId}: " +
                    "offPeak(req=$requiresOffPeak, ok=$canRunOffPeak), " +
                    "charging(req=$requiresCharging, ok=$canRunCharging), " +
                    "wifi(req=$requiresWifi, ok=$canRunWifi)")

                canRunOffPeak && canRunCharging && canRunWifi
            }

            Log.d("DownloadScheduler", "Next item: ${nextItem?.id}")

            if (nextItem == null) {
                scheduleAlarmCheck()
                return@runCatchingCancellable Result.success()
            }

            val manga = mangaDataRepository.findMangaById(nextItem.mangaId, withChapters = true)
            if (manga == null) {
                downloadQueueRepository.removeFromQueue(nextItem.id)
                return@runCatchingCancellable Result.retry()
            }

            val requiresCharging = if (force) false else (nextItem.charging_only || settings.isDownloadOnlyOnChargingEnabled)
            val task = DownloadTask(
                mangaId = nextItem.mangaId,
                isPaused = false,
                isSilent = true,
                chaptersIds = nextItem.chaptersIds,
                destination = null, // Use default
                format = null, // Use default
                allowMeteredNetwork = force || !nextItem.wifiOnly,
                requiresCharging = requiresCharging,
            )

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (!force && nextItem.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresCharging(requiresCharging)
                        .build()
                )
                .addTag(DownloadWorker.TAG)
                .addTag(TAG_QUEUED_DOWNLOAD + nextItem.id)
                .setInputData(task.toData())
                .build()

            Log.d("DownloadScheduler", "Enqueuing download for manga ${nextItem.mangaId}")
            workManager.enqueueUniqueWork(
                "download_${nextItem.mangaId}",
                ExistingWorkPolicy.KEEP,
                downloadRequest
            )

            downloadQueueRepository.removeFromQueue(nextItem.id)

            // Schedule next check to process the rest of the queue
            scheduleNextCheck()

            Result.success()
        }.getOrElse {
            it.printStackTraceDebug("DownloadSchedulerWorker")
            Result.retry()
        }
    }

    private fun isOffPeakTime(): Boolean {
        return isOffPeakTime(settings)
    }

    private fun isCharging(): Boolean {
        val intent = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isMetered(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    private fun scheduleNextCheck(delay: Long = 10, unit: TimeUnit = TimeUnit.SECONDS) {
        enqueue(workManager, unit.toMillis(delay))
    }

    private suspend fun scheduleAlarmCheck() {
        val queue = downloadQueueRepository.getQueue()
        if (queue.isEmpty()) return

        val needsCharging = queue.any { it.charging_only || settings.isDownloadOnlyOnChargingEnabled }
        val needsOffPeak = queue.any { it.offPeakOnly || settings.isDownloadOffPeakEnabled }
        val needsWifi = queue.any { it.wifiOnly }

        if (needsOffPeak) {
            val delayInSeconds = calculateSecondsUntilOffPeak()
            Log.d("DownloadScheduler", "Scheduling alarm check for off-peak in $delayInSeconds seconds")
            if (delayInSeconds > 0) {
                scheduleNextCheck(delayInSeconds, TimeUnit.SECONDS)
            }
        }

        if (needsCharging && !isCharging()) {
            Log.d("DownloadScheduler", "Scheduling alarm check for charging")
            val request = OneTimeWorkRequestBuilder<DownloadSchedulerWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .addTag(TAG_SCHEDULER + "_charging")
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_SCHEDULER + "_charging",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        if (needsWifi && isMetered()) {
            Log.d("DownloadScheduler", "Scheduling alarm check for Wi-Fi")
            val request = OneTimeWorkRequestBuilder<DownloadSchedulerWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .addTag(TAG_SCHEDULER + "_wifi")
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_SCHEDULER + "_wifi",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun calculateSecondsUntilOffPeak(): Long {
        if (!settings.isDownloadOffPeakEnabled) return 0
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentSecond = now.get(Calendar.SECOND)
        val currentTimeInSeconds = (currentHour * 60 + currentMinute) * 60 + currentSecond

        val startParts = settings.downloadOffPeakStart.split(":")
        if (startParts.size != 2) return 15 * 60

        val startSeconds = ((startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)) * 60

        var diff = startSeconds - currentTimeInSeconds
        if (diff <= 0) {
            diff += 24 * 60 * 60
        }
        return diff.toLong()
    }

    companion object {
        const val TAG_SCHEDULER = "download_scheduler"
        const val TAG_QUEUED_DOWNLOAD = "queued_download_"
        const val WORK_NAME_SCHEDULER = "download_scheduler_periodic"
        const val KEY_FORCE = "force"

        fun enqueue(workManager: WorkManager, delay: Long = 0, force: Boolean = false) {
            Log.d("DownloadScheduler", "Enqueuing scheduler with delay: $delay ms, force=$force")
            val request = OneTimeWorkRequestBuilder<DownloadSchedulerWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.Data.Builder().putBoolean(KEY_FORCE, force).build())
                .addTag(TAG_SCHEDULER)
                .build()
            workManager.enqueueUniqueWork(
                WORK_NAME_SCHEDULER,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleAlarm(workManager: WorkManager, settings: AppSettings) {
            if (!settings.isDownloadOffPeakEnabled || isOffPeakTime(settings)) {
                enqueue(workManager)
                return
            }
            
            val delayInSeconds = calculateSecondsUntilOffPeak(settings)
            Log.d("DownloadScheduler", "Scheduling alarm check in $delayInSeconds seconds")

            val request = OneTimeWorkRequestBuilder<DownloadSchedulerWorker>()
                .setInitialDelay(delayInSeconds, TimeUnit.SECONDS)
                .addTag(TAG_SCHEDULER)
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_SCHEDULER,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun isOffPeakTime(settings: AppSettings): Boolean {
            if (!settings.isDownloadOffPeakEnabled) return true
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentTimeInMinutes = currentHour * 60 + currentMinute

            val startParts = settings.downloadOffPeakStart.split(":")
            val endParts = settings.downloadOffPeakEnd.split(":")

            if (startParts.size != 2 || endParts.size != 2) return true

            val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            val isOffPeak = if (startMinutes < endMinutes) {
                currentTimeInMinutes in startMinutes until endMinutes
            } else {
                currentTimeInMinutes !in endMinutes..<startMinutes
            }
            Log.d("DownloadScheduler", "isOffPeakTime: $isOffPeak (now=$currentTimeInMinutes, start=$startMinutes, end=$endMinutes)")
            return isOffPeak
        }

        private fun calculateSecondsUntilOffPeak(settings: AppSettings): Long {
            if (!settings.isDownloadOffPeakEnabled) return 0
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentSecond = now.get(Calendar.SECOND)
            val currentTimeInSeconds = (currentHour * 60 + currentMinute) * 60 + currentSecond

            val startParts = settings.downloadOffPeakStart.split(":")
            if (startParts.size != 2) return 15 * 60

            val startSeconds = ((startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)) * 60

            var diff = startSeconds - currentTimeInSeconds
            if (diff <= 0) {
                diff += 24 * 60 * 60
            }
            return diff.toLong()
        }
    }
}
