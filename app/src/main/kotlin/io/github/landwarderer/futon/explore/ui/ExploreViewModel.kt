package io.github.landwarderer.futon.explore.ui

import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.MangaSourceInfo
import io.github.landwarderer.futon.core.model.isNovelSource
import io.github.landwarderer.futon.core.os.AppShortcutManager
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsFlow
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.ui.util.ReversibleAction
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.combine
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.data.SourcePreset
import io.github.landwarderer.futon.explore.data.SourcePresetsRepository
import io.github.landwarderer.futon.explore.domain.ExploreRepository
import io.github.landwarderer.futon.explore.ui.model.ExploreButtons
import io.github.landwarderer.futon.explore.ui.model.MangaSourceItem
import io.github.landwarderer.futon.explore.ui.model.RecommendationsItem
import io.github.landwarderer.futon.list.ui.model.EmptyHint
import io.github.landwarderer.futon.list.ui.model.ListHeader
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingState
import io.github.landwarderer.futon.list.ui.model.MangaCompactListModel
import io.github.landwarderer.futon.suggestions.domain.SuggestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine as combineFlows
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val presetsRepository: SourcePresetsRepository,
	private val shortcutManager: AppShortcutManager,
) : BaseViewModel() {

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	val appMode = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_APP_MODE,
		valueProducer = { appMode },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	/** The active preset, or null when there is none or it no longer exists (deleted, not restored...). */
	private val activePresetFlow: StateFlow<SourcePreset?> = settings.observeAsFlow(
		key = AppSettings.KEY_ACTIVE_SOURCE_PRESET,
		valueProducer = { activeSourcePresetId },
	).flatMapLatest { id ->
		if (id == SourcePresetsRepository.NO_PRESET) flowOf(null) else presetsRepository.observe(id)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val presets: StateFlow<List<SourcePreset>> = presetsRepository.observeAll()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	val activePresetId: Long
		get() = activePresetFlow.value?.id ?: SourcePresetsRepository.NO_PRESET

	val isPresetActive: Boolean
		get() = activePresetFlow.value != null

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)

	val content: StateFlow<List<ListModel>> = isLoading.flatMapLatest { loading ->
		if (loading) {
			flowOf(getLoadingStateList())
		} else {
			createContentFlow()
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, getLoadingStateList())

	init {
		launchJob(Dispatchers.IO) {
			if (!settings.isSuggestionsEnabled && settings.isTipEnabled(TIP_SUGGESTIONS)) {
				onShowSuggestionsTip.call(Unit)
			}
		}
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.IO) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun setActivePreset(presetId: Long) {
		settings.activeSourcePresetId = presetId
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.IO) {
			val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
			val preset = activePresetFlow.value
			if (preset != null) {
				// With a preset active, "disable" takes the sources out of the preset, not out of the app.
				val removed = sources.mapTo(HashSet(sources.size)) { it.name }
				presetsRepository.updatePresetSources(preset.id, preset.sources - removed)
				onActionDone.call(ReversibleAction(message, null))
			} else {
				val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
				onActionDone.call(ReversibleAction(message, rollback))
			}
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.IO) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.IO) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	private fun observeSourcesForDisplay(): Flow<List<MangaSourceInfo>> = activePresetFlow.flatMapLatest { preset ->
		if (preset != null) {
			sourcesRepository.observePresetSources(preset)
		} else {
			sourcesRepository.observeEnabledSources()
		}
	}

	/** The active preset (or the enabled sources) narrowed to the current manga / novel mode */
	private fun observeFilteredSources(): Flow<List<MangaSourceInfo>> = combineFlows(
		observeSourcesForDisplay(),
		appMode,
	) { sources, mode ->
		val isNovel = mode.isNovel
		sources.filter { it.mangaSource.isNovelSource == isNovel }
	}

	private fun createContentFlow(): Flow<List<ListModel>> = combine(
		observeFilteredSources(),
		getSuggestionFlow(),
		isGrid,
		isRandomLoading,
		isAllSourcesEnabled,
		sourcesRepository.observeHasNewSourcesForBadge(),
		activePresetFlow,
	) { content, suggestions, grid, randomLoading, allSourcesEnabled, newSources, activePreset ->
		buildList(content, suggestions, grid, randomLoading, allSourcesEnabled, newSources, activePreset)
	}.withErrorHandling()

	private fun buildList(
		sources: List<MangaSourceInfo>,
		recommendation: List<Manga>,
		isGrid: Boolean,
		randomLoading: Boolean,
		allSourcesEnabled: Boolean,
		hasNewSources: Boolean,
		activePreset: SourcePreset?,
	): List<ListModel> {
		val result = ArrayList<ListModel>(sources.size + 3)
		result += ExploreButtons(randomLoading, activePreset?.title)
		if (recommendation.isNotEmpty()) {
			result += ListHeader(R.string.suggestions, R.string.more, R.id.nav_suggestions)
			result += RecommendationsItem(recommendation.toRecommendationList())
		}
		if (sources.isNotEmpty()) {
			result += ListHeader(
				textRes = R.string.remote_sources,
				buttonTextRes = if (allSourcesEnabled && activePreset == null) R.string.manage else R.string.catalog,
				badge = if (!allSourcesEnabled && activePreset == null && hasNewSources) "" else null,
			)
			sources.mapTo(result) { MangaSourceItem(it, isGrid) }
		} else {
			result += EmptyHint(
				icon = R.drawable.ic_empty_common,
				textPrimary = R.string.no_manga_sources,
				textSecondary = R.string.no_manga_sources_text,
				actionStringRes = R.string.catalog,
			)
		}
		return result
	}

	private fun getLoadingStateList() = listOf(
		ExploreButtons(isRandomLoading.value, activePresetFlow.value?.title),
		LoadingState(),
	)

	private fun getSuggestionFlow() = isSuggestionsEnabled.mapLatest { isEnabled ->
		if (isEnabled) {
			runCatchingCancellable {
				suggestionRepository.getRandomList(SUGGESTIONS_COUNT)
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	private fun List<Manga>.toRecommendationList() = skipBlacklistedTags().map { manga ->
		MangaCompactListModel(
			manga = manga,
			override = null,
			subtitle = manga.tags.joinToString { it.title },
			counter = 0,
		)
	}

	private fun List<Manga>.skipBlacklistedTags(): List<Manga> {
		val blacklist = settings.tagsBlacklist
		if (blacklist.isEmpty()) {
			return this
		}
		return filterNot { manga ->
			manga.tags.any { it.title.lowercase() in blacklist }
		}
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}
