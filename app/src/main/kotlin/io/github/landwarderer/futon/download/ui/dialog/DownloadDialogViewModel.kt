package io.github.landwarderer.futon.download.ui.dialog

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import androidx.collection.MutableLongLongMap
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.getPreferredBranch
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.DownloadFormat
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.download.ui.worker.DownloadTask
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.data.LocalStorageManager
import io.github.landwarderer.futon.parsers.model.Manga
import io.github.landwarderer.futon.parsers.util.mapToSet
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.parsers.util.sizeOrZero
import io.github.landwarderer.futon.parsers.util.suspendlazy.suspendLazy
import io.github.landwarderer.futon.settings.storage.DirectoryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadDialogViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val scheduler: DownloadWorker.Scheduler,
	private val localStorageManager: LocalStorageManager,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	val manga = savedStateHandle.require<Array<ParcelableManga>>(AppRouter.KEY_MANGA).map {
		it.manga
	}
	private val mangaDetails = suspendLazy {
		coroutineScope {
			manga.map { m ->
				async { m.getDetails() }
			}.awaitAll()
		}
	}
	val onScheduled = MutableEventFlow<Boolean>()
	val defaultFormat = MutableStateFlow<DownloadFormat?>(null)
	val availableDestinations = MutableStateFlow(listOf(defaultDestination()))
	val chaptersSelectOptions = MutableStateFlow(
		ChapterSelectOptions(
			wholeManga = ChaptersSelectMacro.WholeManga(0),
			wholeBranch = null,
			firstChapters = null,
			unreadChapters = null,
		),
	)
	val isOptionsLoading = MutableStateFlow(true)

	init {
		launchJob(Dispatchers.IO) {
			defaultFormat.value = settings.preferredDownloadFormat
		}
		launchJob(Dispatchers.IO) {
			try {
				loadAvailableOptions()
			} finally {
				isOptionsLoading.value = false
			}
		}
		loadAvailableDestinations()
	}

	fun confirm(
		startNow: Boolean,
		chaptersMacro: ChaptersSelectMacro,
		format: DownloadFormat?,
		destination: DirectoryModel?,
		allowMetered: Boolean,
	) {
		launchLoadingJob(Dispatchers.IO) {
			val tasks = mangaDetails.get().map { m ->
				val chapters = checkNotNull(m.chapters) { "Manga \"${m.title}\" cannot be loaded" }
				m to DownloadTask(
					mangaId = m.id,
					isPaused = !startNow,
					isSilent = false,
					chaptersIds = chaptersMacro.getChaptersIds(m.id, chapters)?.toLongArray(),
					destination = destination?.file,
					format = format,
					allowMeteredNetwork = allowMetered,
				)
			}
			scheduler.schedule(tasks)
			onScheduled.call(startNow)
		}
	}

	fun setSelectedBranch(branch: String?) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			wholeBranch = snapshot.wholeBranch?.copy(branch),
		)
	}

	fun setFirstChaptersCount(count: Int) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			firstChapters = snapshot.firstChapters?.copy(count),
		)
	}

	fun setUnreadChaptersCount(count: Int) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			unreadChapters = snapshot.unreadChapters?.copy(count),
		)
	}

	private fun defaultDestination() = DirectoryModel(
		title = null,
		titleRes = R.string.system_default,
		file = null,
		isRemovable = false,
		isChecked = true,
		isAvailable = true,
	)

	private suspend fun loadAvailableOptions() {
		val details = mangaDetails.get()
		var totalChapters = 0
		val branches = ArrayMap<String?, Int>()
		var maxChapters = 0
		var maxUnreadChapters = 0
		val preferredBranches = ArraySet<String?>(details.size)
		val currentChaptersIds = MutableLongLongMap(details.size)

		details.forEach { m ->
			val history = historyRepository.getOne(m)
			if (history != null) {
				currentChaptersIds[m.id] = history.chapterId
				val unreadChaptersCount = m.chapters?.dropWhile { it.id != history.chapterId }.sizeOrZero()
				maxUnreadChapters = maxOf(maxUnreadChapters, unreadChaptersCount)
			} else {
				maxUnreadChapters = maxOf(maxUnreadChapters, m.chapters.sizeOrZero())
			}
			maxChapters = maxOf(maxChapters, m.chapters.sizeOrZero())
			preferredBranches.add(m.getPreferredBranch(history))
			m.chapters?.forEach { c ->
				totalChapters++
				branches.increment(c.branch)
			}
		}
		val defaultBranch = preferredBranches.firstOrNull()
		chaptersSelectOptions.value = ChapterSelectOptions(
			wholeManga = ChaptersSelectMacro.WholeManga(totalChapters),
			wholeBranch = if (branches.size > 1) {
				ChaptersSelectMacro.WholeBranch(
					branches = branches,
					selectedBranch = defaultBranch,
				)
			} else {
				null
			},
			firstChapters = if (maxChapters > 0) {
				ChaptersSelectMacro.FirstChapters(
					chaptersCount = minOf(5, maxChapters),
					maxAvailableCount = maxChapters,
					branch = defaultBranch,
				)
			} else {
				null
			},
			unreadChapters = if (currentChaptersIds.isNotEmpty()) {
				ChaptersSelectMacro.UnreadChapters(
					chaptersCount = minOf(5, maxUnreadChapters),
					maxAvailableCount = maxUnreadChapters,
					currentChaptersIds = currentChaptersIds,
				)
			} else {
				null
			},
		)
	}

	private fun loadAvailableDestinations() = launchJob(Dispatchers.IO) {
		val defaultDir = manga.mapToSet {
			localMangaRepository.getOutputDir(it, null)
		}.singleOrNull()
		val dirs = localStorageManager.getWriteableDirs()
		availableDestinations.value = buildList(dirs.size + 1) {
			if (defaultDir == null) {
				add(defaultDestination())
			} else if (defaultDir !in dirs) {
				add(
					DirectoryModel(
						title = localStorageManager.getDirectoryDisplayName(defaultDir, isFullPath = false),
						titleRes = 0,
						file = defaultDir,
						isChecked = true,
						isAvailable = true,
						isRemovable = false,
					),
				)
			}
			dirs.mapTo(this) { dir ->
				DirectoryModel(
					title = localStorageManager.getDirectoryDisplayName(dir, isFullPath = false),
					titleRes = 0,
					file = dir,
					isChecked = dir == defaultDir,
					isAvailable = true,
					isRemovable = false,
				)
			}
		}
	}

	private suspend fun Manga.getDetails(): Manga = runCatchingCancellable {
		mangaRepositoryFactory.create(source).getDetails(this)
	}.onFailure { e ->
		e.printStackTraceDebug("DownloadDialogViewModel::Manga")
	}.getOrDefault(this)

	private fun <T> MutableMap<T, Int>.increment(key: T) {
		put(key, getOrDefault(key, 0) + 1)
	}
}
