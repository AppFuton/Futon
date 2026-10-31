package io.github.landwarderer.futon.tracker.work

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dagger.Lazy
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.model.MangaSource
import io.github.landwarderer.futon.core.exceptions.resolve.CaptchaHandler
import io.github.landwarderer.futon.core.model.ids
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.TrackerDownloadStrategy
import io.github.landwarderer.futon.core.prefs.TriStateOption
import io.github.landwarderer.futon.core.util.ext.awaitUniqueWorkInfoByName
import io.github.landwarderer.futon.core.util.ext.checkNotificationPermission
import io.github.landwarderer.futon.core.util.ext.findCloudFlareException
import io.github.landwarderer.futon.core.util.ext.onEachIndexed
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.trySetForeground
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.domain.EnforceStorageQuotaUseCase
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.toIntUp
import io.github.landwarderer.futon.settings.work.PeriodicWorkScheduler
import io.github.landwarderer.futon.tracker.domain.CheckNewChaptersUseCase
import io.github.landwarderer.futon.tracker.domain.GetTracksUseCase
import io.github.landwarderer.futon.tracker.domain.TrackingRepository
import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import io.github.landwarderer.futon.tracker.domain.model.MangaUpdates
import io.github.landwarderer.futon.tracker.work.TrackerNotificationHelper.NotificationInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.roundToInt
import androidx.appcompat.R as appcompatR

@HiltWorker
class TrackWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val captchaHandler: CaptchaHandler,
	private val notificationHelper: TrackerNotificationHelper,
	private val settings: AppSettings,
	private val getTracksUseCase: GetTracksUseCase,
	private val checkNewChaptersUseCase: CheckNewChaptersUseCase,
	private val trackingRepository: TrackingRepository,
	private val workManager: WorkManager,
	private val localRepositoryLazy: Lazy<LocalMangaRepository>,
	private val mangaDataRepositoryLazy: Lazy<MangaDataRepository>,
	private val downloadQueueRepositoryLazy: Lazy<DownloadQueueRepository>,
	private val historyRepositoryLazy: Lazy<HistoryRepository>,
	private val enforceStorageQuotaUseCaseLazy: Lazy<EnforceStorageQuotaUseCase>,
) : CoroutineWorker(context, workerParams) {

	private val notificationManager by lazy { NotificationManagerCompat.from(applicationContext) }
	private var isStorageQuotaReached: Boolean? = null

	override suspend fun doWork(): Result {
		notificationHelper.updateChannels()
		trySetForeground()
		// Full run when manually triggered (TAG_ONESHOT), regardless of whether the
		// foreground service promotion succeeded - Android may refuse the promotion
		// on certain OEMs, but the user explicitly asked for an immediate full check.
		val isFullRun = TAG_ONESHOT in tags
		val recheckSource = inputData.getString(KEY_SOURCE)?.let { MangaSource(it) }
		return try {
			doWorkImpl(isFullRun = isFullRun, recheckSource = recheckSource)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug("TrackWorker::doWork")
			Result.failure()
		} finally {
			withContext(NonCancellable) {
				notificationManager.cancel(WORKER_NOTIFICATION_ID)
			}
		}
	}

	private suspend fun doWorkImpl(isFullRun: Boolean, recheckSource: MangaSource?): Result {
		if (!settings.isTrackerEnabled) {
			return Result.success()
		}
		val tracks = if (recheckSource != null) {
			// Right after a captcha was solved: only the manga of that source which stopped at the captcha
			trackingRepository.getCaptchaBlockedTracks(recheckSource)
		} else {
			getTracksUseCase(if (isFullRun) Int.MAX_VALUE else BATCH_SIZE)
		}
		if (tracks.isEmpty()) {
			return Result.success()
		}

		val notifications = checkUpdatesAsync(tracks)
		if (notifications.isNotEmpty() && applicationContext.checkNotificationPermission(null)) {
			val groupNotification = notificationHelper.createGroupNotification(notifications)
			notifications.forEach { notificationManager.notify(it.tag, it.id, it.notification) }
			if (groupNotification != null) {
				notificationManager.notify(TAG, TrackerNotificationHelper.GROUP_NOTIFICATION_ID, groupNotification)
			}
		}
		return Result.success()
	}

	@CheckResult
	private suspend fun checkUpdatesAsync(tracks: List<MangaTracking>): List<NotificationInfo> {
		val semaphore = Semaphore(MAX_PARALLELISM)
		val sourceSemaphores = HashMap<MangaSource, Semaphore>()
		// Sources that failed in a way every other request of this run would repeat, with that failure
		val blockedSources = ConcurrentHashMap<MangaSource, MangaUpdates.Failure>()
		val skipped = ConcurrentHashMap<MangaSource, MutableList<Long>>()
		val notifications = channelFlow<MangaUpdates?> {
			for (track in tracks) {
				val source = track.manga.source
				val sourceSemaphore = sourceSemaphores.getOrPut(source) { Semaphore(MAX_PER_SOURCE_PARALLELISM) }
				launch {
					// The source permit comes first: a busy source must not hold the global slots while it waits
					sourceSemaphore.withPermit {
						if (blockedSources.containsKey(source)) {
							skipped.getOrPut(source) { Collections.synchronizedList(ArrayList()) }.add(track.manga.id)
							send(null)
							return@withPermit
						}
						val updates = semaphore.withPermit {
							runCatchingCancellable {
								checkNewChaptersUseCase.invoke(track)
							}.getOrElse { error ->
								MangaUpdates.Failure(
									manga = track.manga,
									error = error,
								)
							}
						}
						if (updates is MangaUpdates.Failure && updates.isSourceBlocked()) {
							blockedSources.putIfAbsent(source, updates)
						}
						send(updates)
					}
				}
			}
		}.onEachIndexed { index, _ ->
			if (applicationContext.checkNotificationPermission(WORKER_CHANNEL_ID)) {
				notificationManager.notify(WORKER_NOTIFICATION_ID, createWorkerNotification(tracks.size, index + 1))
			}
		}.filterNotNull().onEach {
			when (it) {
				is MangaUpdates.Failure -> {
					val e = it.error?.findCloudFlareException()
					if (e != null) {
						captchaHandler.handle(e, tryAutoResolve = false)
					}
				}

				is MangaUpdates.Success -> processDownload(it)
			}
		}.mapNotNull {
			when (it) {
				is MangaUpdates.Failure -> null
				is MangaUpdates.Success -> if (it.isValid && it.isNotEmpty()) {
					notificationHelper.createNotification(
						manga = it.manga,
						newChapters = it.newChapters,
					)
				} else {
					null
				}
			}
		}.toList()
		for ((source, ids) in skipped) {
			val failure = blockedSources[source] ?: continue
			trackingRepository.saveSkipped(ids, failure)
		}
		return notifications
	}

	override suspend fun getForegroundInfo(): ForegroundInfo {
		val channel = NotificationChannelCompat.Builder(
			WORKER_CHANNEL_ID,
			NotificationManagerCompat.IMPORTANCE_LOW,
		)
			.setName(applicationContext.getString(R.string.check_for_new_chapters))
			.setShowBadge(false)
			.setVibrationEnabled(false)
			.setSound(null, null)
			.setLightsEnabled(false)
			.build()
		notificationManager.createNotificationChannel(channel)

		val notification = createWorkerNotification(0, 0)
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(WORKER_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			ForegroundInfo(WORKER_NOTIFICATION_ID, notification)
		}
	}

	private fun createWorkerNotification(max: Int, progress: Int) = NotificationCompat.Builder(
		applicationContext,
		WORKER_CHANNEL_ID,
	).apply {
		setContentTitle(applicationContext.getString(R.string.check_for_new_chapters))
		setPriority(NotificationCompat.PRIORITY_MIN)
		setCategory(NotificationCompat.CATEGORY_SERVICE)
		setDefaults(0)
		setOngoing(false)
		setOnlyAlertOnce(true)
		setSilent(true)
		setContentIntent(
			PendingIntentCompat.getActivity(
				applicationContext,
				0,
				AppRouter.trackerSettingsIntent(applicationContext),
				0,
				false,
			),
		)
		addAction(
			appcompatR.drawable.abc_ic_clear_material,
			applicationContext.getString(android.R.string.cancel),
			workManager.createCancelPendingIntent(id),
		)
		setProgress(max, progress, max == 0)
		setSmallIcon(android.R.drawable.stat_notify_sync)
		setForegroundServiceBehavior(
			if (TAG_ONESHOT in tags) {
				NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
			} else {
				NotificationCompat.FOREGROUND_SERVICE_DEFERRED
			},
		)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val actionIntent = PendingIntentCompat.getActivity(
				applicationContext, SETTINGS_ACTION_CODE,
				Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
					.putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
					.putExtra(Settings.EXTRA_CHANNEL_ID, WORKER_CHANNEL_ID),
				0, false,
			)
			addAction(
				R.drawable.ic_settings,
				applicationContext.getString(R.string.notifications_settings),
				actionIntent,
			)
		}
	}.build()

	private suspend fun processDownload(mangaUpdates: MangaUpdates.Success) {
		if (!mangaUpdates.isValid || mangaUpdates.newChapters.isEmpty()) {
			return
		}
		val strategy = settings.trackerDownloadStrategy
		if (strategy == TrackerDownloadStrategy.DISABLED) {
			return
		}
		val manga = mangaUpdates.manga
		val localManga = localRepositoryLazy.get().findSavedManga(manga, withDetails = true)
		val shouldDownload = when (strategy) {
			TrackerDownloadStrategy.DISABLED -> false
			TrackerDownloadStrategy.DOWNLOADED -> localManga != null
			TrackerDownloadStrategy.RECENTLY_READ -> isReadRecently(manga)
		}
		if (!shouldDownload) {
			return
		}
		val savedChapters = localManga?.manga?.chapters?.ids().orEmpty()
		val chapters = mangaUpdates.newChapters
			.filterNot { it.id in savedChapters }
			.let {
				// Only the newest few for recently read manga: a dormant series that backfills its archive
				// would otherwise queue hundreds of chapters at once. Downloaded manga keep every new
				// chapter, as before - the ones left out would never be offered again.
				if (strategy == TrackerDownloadStrategy.RECENTLY_READ) {
					it.takeLast(TrackerDownloadStrategy.MAX_CHAPTERS_PER_RUN)
				} else {
					it
				}
			}
		if (chapters.isEmpty() || !hasFreeSpaceForDownload(manga) || isStorageQuotaReached()) {
			return
		}
		// the download worker resolves the chapters from the database, which must know the new ones
		mangaDataRepositoryLazy.get().storeManga(manga, replaceExisting = true)
		// Through the download queue, so the off-peak, charging and Wi-Fi rules apply
		downloadQueueRepositoryLazy.get().addToQueue(
			manga = manga,
			chaptersIds = chapters.ids().toLongArray(),
			wifiOnly = settings.allowDownloadOnMeteredNetwork == TriStateOption.DISABLED,
			chargingOnly = settings.isDownloadOnlyOnChargingEnabled,
			offPeakOnly = settings.isDownloadOffPeakEnabled,
			isPaused = false,
		)
	}

	private suspend fun isReadRecently(manga: Manga): Boolean {
		val lastRead = historyRepositoryLazy.get().getOne(manga)?.updatedAt ?: return false
		val threshold = Instant.now().minus(TrackerDownloadStrategy.RECENT_READ_WINDOW_DAYS, ChronoUnit.DAYS)
		return lastRead.isAfter(threshold)
	}

	/**
	 * Auto-download is unattended, so it must not be the thing that fills the device.
	 * Checks the volume this manga would actually be written to; a directory whose free space
	 * cannot be read (reported as zero) does not block the download.
	 */
	private suspend fun hasFreeSpaceForDownload(manga: Manga): Boolean = runCatchingCancellable {
		val dir = localRepositoryLazy.get().getOutputDir(manga, null) ?: return@runCatchingCancellable false
		val available = runInterruptible(Dispatchers.IO) { dir.usableSpace }
		available == 0L || available >= TrackerDownloadStrategy.MIN_FREE_SPACE_BYTES
	}.getOrDefault(true)

	/**
	 * Reaching the storage quota makes the download worker evict the oldest downloaded chapters,
	 * which an unattended download should not do: skip auto-downloads until there is room again.
	 * Computed once per run, as it walks the whole local storage.
	 */
	private suspend fun isStorageQuotaReached(): Boolean {
		isStorageQuotaReached?.let { return it }
		val usage = runCatchingCancellable {
			enforceStorageQuotaUseCaseLazy.get().getUsage()
		}.getOrNull()
		val result = usage != null && usage.currentBytes >= usage.totalBytes
		isStorageQuotaReached = result
		return result
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		private val settings: AppSettings,
		private val dbProvider: Provider<MangaDatabase>,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val frequency = settings.trackerFrequencyFactor
			if (frequency <= 0f) {
				return unschedule()
			}
			val constraints = createConstraints()
			val runCount = dbProvider.get().getTracksDao().getTracksCount()
			val runsPerFullCheck = (runCount / BATCH_SIZE.toFloat()).toIntUp().coerceAtLeast(1)
			val interval = (18 / runsPerFullCheck / frequency).roundToInt().coerceAtLeast(2)
			val request = PeriodicWorkRequestBuilder<TrackWorker>(interval.toLong(), TimeUnit.HOURS)
				.setConstraints(constraints)
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager
				.cancelUniqueWork(TAG)
				.await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager
				.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}

		fun startNow() {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()
			val request = OneTimeWorkRequestBuilder<TrackWorker>()
				.setConstraints(constraints)
				.addTag(TAG_ONESHOT)
				.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
				.build()
			workManager.enqueue(request)
		}

		/**
		 * Checks again the manga of [source] that stopped at its captcha, once that captcha is solved.
		 * A re-check already waiting or running for the source is kept.
		 */
		fun recheckSource(source: MangaSource) {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()
			val request = OneTimeWorkRequestBuilder<TrackWorker>()
				.setConstraints(constraints)
				.setInputData(workDataOf(KEY_SOURCE to source.name))
				.addTag(TAG_RECHECK)
				.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
				.build()
			workManager.enqueueUniqueWork("${TAG_RECHECK}_${source.name}", ExistingWorkPolicy.KEEP, request)
		}

		fun observeIsRunning(): Flow<Boolean> {
			val query = WorkQuery.Builder.fromTags(listOf(TAG, TAG_ONESHOT, TAG_RECHECK)).build()
			return workManager.getWorkInfosFlow(query)
				.map { works ->
					works.any { x -> x.state == WorkInfo.State.RUNNING }
				}
		}

		private fun createConstraints() = Constraints.Builder()
			.setRequiredNetworkType(if (settings.isTrackerWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
			.build()
	}

	private companion object {

		const val WORKER_CHANNEL_ID = "track_worker"
		const val WORKER_NOTIFICATION_ID = 35
		const val TAG = "tracking"
		const val TAG_ONESHOT = "tracking_oneshot"
		const val TAG_RECHECK = "tracking_recheck"
		const val KEY_SOURCE = "source"
		const val MAX_PARALLELISM = 6
		const val MAX_PER_SOURCE_PARALLELISM = 2
		val BATCH_SIZE = if (BuildConfig.DEBUG) 20 else 46
		const val SETTINGS_ACTION_CODE = 5
	}
}
