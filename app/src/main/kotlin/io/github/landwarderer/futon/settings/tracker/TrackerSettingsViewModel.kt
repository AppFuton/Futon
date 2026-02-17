package io.github.landwarderer.futon.settings.tracker

import androidx.room.InvalidationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Closeable
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.TABLE_FAVOURITE_CATEGORIES
import io.github.landwarderer.futon.core.db.removeObserverAsync
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.tracker.domain.TrackingRepository
import javax.inject.Inject

@HiltViewModel
class TrackerSettingsViewModel @Inject constructor(
	private val repository: TrackingRepository,
	private val database: MangaDatabase,
) : BaseViewModel() {

	val categoriesCount = MutableStateFlow<IntArray?>(null)

	init {
		updateCategoriesCount()
		val databaseObserver = DatabaseObserver(this)
		addCloseable(databaseObserver)
		launchJob(Dispatchers.IO) {
			database.invalidationTracker.addObserver(databaseObserver)
		}
	}

	private fun updateCategoriesCount() {
		launchJob(Dispatchers.IO) {
			categoriesCount.value = repository.getCategoriesCount()
		}
	}

	private class DatabaseObserver(private var vm: TrackerSettingsViewModel?) :
		InvalidationTracker.Observer(arrayOf(TABLE_FAVOURITE_CATEGORIES)),
		Closeable {

		override fun onInvalidated(tables: Set<String>) {
			vm?.updateCategoriesCount()
		}

		override fun close() {
			(vm ?: return).database.invalidationTracker.removeObserverAsync(this)
			vm = null
		}
	}
}
