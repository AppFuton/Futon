package io.github.landwarderer.futon.settings.tracker.categories

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.model.FavouriteCategory
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.favourites.domain.FavouritesRepository
import javax.inject.Inject

@HiltViewModel
class TrackerCategoriesConfigViewModel @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val content = favouritesRepository.observeCategories()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	private var updateJob: Job? = null

	fun toggleItem(category: FavouriteCategory) {
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob?.join()
			favouritesRepository.updateCategoryTracking(category.id, !category.isTrackingEnabled)
		}
	}
}
