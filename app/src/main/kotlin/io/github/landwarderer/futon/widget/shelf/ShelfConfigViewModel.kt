package io.github.landwarderer.futon.widget.shelf

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.favourites.domain.FavouritesRepository
import io.github.landwarderer.futon.widget.shelf.model.CategoryItem
import javax.inject.Inject

@HiltViewModel
class ShelfConfigViewModel @Inject constructor(
	favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	private val selectedCategoryId = MutableStateFlow(0L)

	val content: StateFlow<List<CategoryItem>> = combine(
		favouritesRepository.observeCategories(),
		selectedCategoryId,
	) { categories, selectedId ->
		val list = ArrayList<CategoryItem>(categories.size + 1)
		list += CategoryItem(0L, null, selectedId == 0L)
		categories.mapTo(list) {
			CategoryItem(it.id, it.title, selectedId == it.id)
		}
		list
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	var checkedId: Long
		get() = selectedCategoryId.value
		set(value) {
			selectedCategoryId.value = value
		}
}
