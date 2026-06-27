package io.github.landwarderer.futon.settings.sources

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class TagsBlacklistViewModel @Inject constructor(
	private val settings: AppSettings,
	@BaseHttpClient private val okHttpClient: OkHttpClient,
) : BaseViewModel() {

	private val _allTags = MutableStateFlow<List<String>>(emptyList())
	private val _searchQuery = MutableStateFlow<String?>(null)
	val allTags: StateFlow<List<String>> = combine(_allTags, _searchQuery) { tags: List<String>, query: String? ->
		if (query.isNullOrBlank()) {
			tags
		} else {
			tags.filter { it.contains(query, ignoreCase = true) }
		}
	}.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

	private val _blacklistedTags = MutableStateFlow(settings.tagsBlacklist)
	val blacklistedTags: StateFlow<Set<String>> = _blacklistedTags.asStateFlow()

	init {
		fetchTags()
	}

	private fun fetchTags() {
		launchJob(Dispatchers.IO) {
			try {
				val request = Request.Builder()
					.url("https://raw.githubusercontent.com/AppFuton/filters/refs/heads/main/data/tags.json")
					.build()
				okHttpClient.newCall(request).execute().use { response ->
					if (response.isSuccessful) {
						val tags = response.body.byteStream().use { stream ->
							Json.decodeFromStream<List<String>>(stream)
						}
						_allTags.value = tags.sorted()
					}
				}
			} finally {
				loadingCounter.decrement()
			}
		}
	}

	fun toggleTag(tag: String) {
		val current = _blacklistedTags.value.toMutableSet()
		if (current.contains(tag)) {
			current.remove(tag)
		} else {
			current.add(tag)
		}
		_blacklistedTags.value = current
		settings.tagsBlacklist = current
	}

	fun performSearch(query: String?) {
		_searchQuery.value = query
	}
}
