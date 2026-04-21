package io.github.landwarderer.futon.settings.sources.catalog

import androidx.annotation.WorkerThread
import androidx.lifecycle.viewModelScope
import androidx.room.invalidationTrackerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.TABLE_SOURCES
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.ui.util.ReversibleAction
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.data.SourcesSortOrder
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingState
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.EnumSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SourcesCatalogViewModel @Inject constructor(
	private val repository: MangaSourcesRepository,
	private val mihonExtensionManager: MihonExtensionManager,
	db: MangaDatabase,
	settings: AppSettings,
) : BaseViewModel() {

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val locales: Set<String?> = buildSet {
		repository.allMangaSources.forEach { add(it.locale) }
		mihonExtensionManager.getMihonMangaSources().forEach { add(it.locale) }
		add(null)
	}

	private val searchQuery = MutableStateFlow<String?>(null)
	val appliedFilter = MutableStateFlow(
		SourcesCatalogFilter(
			types = emptySet(),
			locale = Locale.getDefault().language.takeIf { it in locales },
			isNewOnly = false,
		),
	)

	val hasNewSources = repository.observeHasNewSources()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, false)

	val contentTypes = MutableStateFlow<List<ContentType>>(emptyList())

	val content: StateFlow<List<ListModel>> = combine(
		searchQuery,
		appliedFilter,
		db.invalidationTrackerFlow(TABLE_SOURCES),
		mihonExtensionManager.installedExtensions,
	) { q, f, _, _ ->
		buildSourcesList(f, q)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		repository.clearNewSourcesBadge()
		launchJob(Dispatchers.IO) {
			contentTypes.value = getContentTypes(settings.isNsfwContentDisabled)
		}
	}

	fun performSearch(query: String?) {
		searchQuery.value = query?.trim()
	}

	fun setLocale(value: String?) {
		appliedFilter.value = appliedFilter.value.copy(locale = value)
	}

	fun addSource(source: MangaSource) {
		launchJob(Dispatchers.IO) {
			val rollback = repository.setSourcesEnabled(setOf(source), true)
			onActionDone.call(ReversibleAction(R.string.source_enabled, rollback))
		}
	}

	fun setContentType(value: ContentType, isAdd: Boolean) {
		val filter = appliedFilter.value
		val types = EnumSet.noneOf(ContentType::class.java)
		types.addAll(filter.types)
		if (isAdd) {
			types.add(value)
		} else {
			types.remove(value)
		}
		appliedFilter.value = filter.copy(types = types)
	}

	fun setNewOnly(value: Boolean) {
		appliedFilter.value = appliedFilter.value.copy(isNewOnly = value)
	}

	private suspend fun buildSourcesList(filter: SourcesCatalogFilter, query: String?): List<SourceCatalogItem> {
		val sources = repository.queryParserSources(
			isDisabledOnly = true,
			isNewOnly = filter.isNewOnly,
			excludeBroken = false,
			types = filter.types,
			query = query,
			locale = filter.locale,
			sortOrder = SourcesSortOrder.ALPHABETIC,
		)
		return if (sources.isEmpty()) {
			listOf(
				if (query == null) {
					SourceCatalogItem.Hint(
						icon = R.drawable.ic_empty_feed,
						title = R.string.no_manga_sources,
						text = R.string.no_manga_sources_catalog_text,
					)
				} else {
					SourceCatalogItem.Hint(
						icon = R.drawable.ic_empty_feed,
						title = R.string.nothing_found,
						text = R.string.no_manga_sources_found,
					)
				},
			)
		} else {
			sources.map {
				SourceCatalogItem.Source(source = it)
			}
		}
	}

	@WorkerThread
	private fun getContentTypes(isNsfwDisabled: Boolean): List<ContentType> {
		val result = buildSet {
			repository.allMangaSources.forEach { add(it.contentType) }
			mihonExtensionManager.getMihonMangaSources().forEach { 
				when(it.contentType) {
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANGA -> add(ContentType.MANGA)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.HENTAI_MANGA -> add(ContentType.HENTAI)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.COMICS -> add(ContentType.COMICS)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANHWA -> add(ContentType.MANHWA)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANHUA -> add(ContentType.MANHUA)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.NOVEL -> add(ContentType.NOVEL)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.ONE_SHOT -> add(ContentType.ONE_SHOT)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.DOUJINSHI -> add(ContentType.DOUJINSHI)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.IMAGE_SET -> add(ContentType.IMAGE_SET)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.ARTIST_CG -> add(ContentType.ARTIST_CG)
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.GAME_CG -> add(ContentType.GAME_CG)
					else -> {}
				}
			}
		}.toList().sortedByDescending { type ->
			val kotatsuCount = repository.allMangaSources.count { it.contentType == type }
			val mihonCount = mihonExtensionManager.getMihonMangaSources().count { 
				when(it.contentType) {
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANGA -> type == ContentType.MANGA
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.HENTAI_MANGA -> type == ContentType.HENTAI
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.COMICS -> type == ContentType.COMICS
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANHWA -> type == ContentType.MANHWA
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.MANHUA -> type == ContentType.MANHUA
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.NOVEL -> type == ContentType.NOVEL
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.ONE_SHOT -> type == ContentType.ONE_SHOT
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.DOUJINSHI -> type == ContentType.DOUJINSHI
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.IMAGE_SET -> type == ContentType.IMAGE_SET
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.ARTIST_CG -> type == ContentType.ARTIST_CG
					io.github.landwarderer.futon.mihon.parsers.model.ContentType.GAME_CG -> type == ContentType.GAME_CG
					else -> false
				}
			}
			kotatsuCount + mihonCount
		}
		return if (isNsfwDisabled) {
			result.filterNot { it == ContentType.HENTAI }
		} else {
			result
		}
	}
}
