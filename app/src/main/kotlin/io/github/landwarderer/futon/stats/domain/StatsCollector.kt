package io.github.landwarderer.futon.stats.domain

import androidx.collection.LongSparseArray
import androidx.collection.set
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.RetainedLifecycleCoroutineScope
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.stats.data.StatsEntity
import javax.inject.Inject

@ViewModelScoped
class StatsCollector @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	lifecycle: ViewModelLifecycle,
) {

	private val viewModelScope = RetainedLifecycleCoroutineScope(lifecycle)
	private val stats = LongSparseArray<Entry>(1)

	@Synchronized
	fun onStateChanged(mangaId: Long, state: ReaderState) {
		if (!settings.isStatsEnabled) {
			return
		}
		val now = System.currentTimeMillis()
		val entry = stats[mangaId]
		if (entry == null) {
			stats[mangaId] = Entry(
				state = state,
				stats = StatsEntity(
					mangaId = mangaId,
					startedAt = now,
					duration = 0,
					pages = 0,
				),
			)
			return
		}
		val pagesDelta = if (entry.state.page != state.page || entry.state.chapterId != state.chapterId) 1 else 0
		val newEntry = entry.copy(
			stats = StatsEntity(
				mangaId = mangaId,
				startedAt = entry.stats.startedAt,
				duration = now - entry.stats.startedAt,
				pages = entry.stats.pages + pagesDelta,
			),
		)
		stats[mangaId] = newEntry
		commit(newEntry.stats)
	}

	@Synchronized
	fun onPause(mangaId: Long) {
		stats.remove(mangaId)
	}

	private fun commit(entity: StatsEntity) {
		viewModelScope.launch(Dispatchers.IO) {
			runCatchingCancellable {
				db.getStatsDao().upsert(entity)
			}.onFailure { e ->
				e.printStackTraceDebug()
			}
		}
	}

	private data class Entry(
		val state: ReaderState,
		val stats: StatsEntity,
	)
}
