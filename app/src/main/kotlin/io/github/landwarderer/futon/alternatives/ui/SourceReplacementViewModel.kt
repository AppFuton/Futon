package io.github.landwarderer.futon.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.alternatives.domain.AlternativeSourceScope
import io.github.landwarderer.futon.alternatives.domain.AlternativeSourceScopeOptions
import io.github.landwarderer.futon.alternatives.domain.AlternativesUseCase
import io.github.landwarderer.futon.alternatives.domain.MigrateUseCase
import io.github.landwarderer.futon.alternatives.domain.SourceReplacementPlan
import io.github.landwarderer.futon.alternatives.domain.SourceReplacementPlannerUseCase
import io.github.landwarderer.futon.core.model.chaptersCount
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.ListMode
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.list.domain.MangaListMapper
import io.github.landwarderer.futon.list.ui.model.ButtonFooter
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.list.ui.model.LoadingFooter
import io.github.landwarderer.futon.list.ui.model.LoadingState
import io.github.landwarderer.futon.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@HiltViewModel
class SourceReplacementViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
	private val alternativesUseCase: AlternativesUseCase,
	private val replacementPlanner: SourceReplacementPlannerUseCase,
	private val migrateUseCase: MigrateUseCase,
	private val mangaListMapper: MangaListMapper,
) : BaseViewModel() {

	private val requestedManga: List<Manga> = savedStateHandle
		.get<List<ParcelableManga>>(AppRouter.KEY_MANGA_LIST)
		?.map(ParcelableManga::manga)
		?.distinctBy(Manga::id)
		?.takeIf(List<Manga>::isNotEmpty)
		?: listOf(savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga)
	private val originSource = requestedManga.first().source
	private var mangaQueue = requestedManga.filter { it.source.name == originSource.name }
	private val originalSourceNames = setOf(originSource.name)
	private var currentIndex = (savedStateHandle.get<Int>(AppRouter.KEY_INDEX) ?: 0)
		.coerceIn(mangaQueue.indices)

	private val _currentManga = MutableStateFlow(mangaQueue[currentIndex])
	val currentManga = _currentManga.asStateFlow()
	val manga: Manga
		get() = _currentManga.value

	private val _queueSize = MutableStateFlow(mangaQueue.size)
	val queueSize = _queueSize.asStateFlow()
	private val _queuePosition = MutableStateFlow(currentIndex + 1)
	val queuePosition = _queuePosition.asStateFlow()

	private val _selectedSource = MutableStateFlow<MangaSource?>(null)
	val selectedSource = _selectedSource.asStateFlow()
	private val _matchedCount = MutableStateFlow(0)
	val matchedCount = _matchedCount.asStateFlow()

	private val results = MutableStateFlow<List<MangaAlternativeModel>>(emptyList())
	private val sourceComparisonItems = MutableStateFlow<List<SourceReplacementModel>>(emptyList())
	private val _isComparingSources = MutableStateFlow(false)
	val isComparingSources = _isComparingSources.asStateFlow()

	private val replacementPlans = MutableStateFlow<List<SourceReplacementPlan>>(emptyList())
	private val failedSourceNames = mutableSetOf<String>()
	private val usedSourceNames = mutableSetOf<String>()
	private val activeMatches = MutableStateFlow<Map<Long, Manga>>(emptyMap())
	private val _isManualMode = MutableStateFlow(false)
	val isManualMode = _isManualMode.asStateFlow()
	private val _isPlanning = MutableStateFlow(true)
	val isPlanning = _isPlanning.asStateFlow()
	private val _planningStep = MutableStateFlow(SourceReplacementStep.COMPARING_SOURCES)
	val planningStep = _planningStep.asStateFlow()
	private val _planningProgress = MutableStateFlow(0)
	val planningProgress = _planningProgress.asStateFlow()
	private val _planningTotal = MutableStateFlow(0)
	val planningTotal = _planningTotal.asStateFlow()
	private val _planningSourcesRemaining = MutableStateFlow(0)
	val planningSourcesRemaining = _planningSourcesRemaining.asStateFlow()
	private val _planningSampleSize = MutableStateFlow(0)
	val planningSampleSize = _planningSampleSize.asStateFlow()
	private val _planningCurrentSource = MutableStateFlow<MangaSource?>(null)
	val planningCurrentSource = _planningCurrentSource.asStateFlow()
	private val _sourceScope = MutableStateFlow<AlternativeSourceScope>(AlternativeSourceScope.Enabled)
	val sourceScope = _sourceScope.asStateFlow()
	private val _scopeOptions = MutableStateFlow<AlternativeSourceScopeOptions?>(null)
	val scopeOptions = _scopeOptions.asStateFlow()
	private var sourceScopeInitialized = false
	private var queueInitialized = false

	private var planningJob: Job? = null
	private var migrationJob: Job? = null
	private var searchJob: Job? = null
	private var manualSourceOrder = emptyList<MangaSource>()
	private val manualTriedSourceNames = mutableSetOf<String>()

	val onPlanReady = MutableEventFlow<PlanReady>()
	val onMigrated = MutableEventFlow<MigrationResult>()
	val onBatchMigrated = MutableEventFlow<BatchMigrationResult>()
	private val _replacementSummary = MutableStateFlow<ReplacementSummary?>(null)
	val replacementSummary = _replacementSummary.asStateFlow()
	private val replacementSourceUsage = linkedMapOf<String, SourceUsage>()
	private var replacementCount = 0

	private val screenState = combine(
		isPlanning,
		isComparingSources,
		sourceComparisonItems,
	) { planning, comparing, sourceItems ->
		ReplacementScreenState(planning, comparing, sourceItems)
	}

	val list: StateFlow<List<ListModel>> = combine(
		results,
		isLoading,
		selectedSource,
		screenState,
	) { result, loading, source, screen ->
		when {
			screen.planning -> emptyList()
			screen.comparing -> screen.sourceItems + if (isManualMode.value) {
				listOf(ButtonFooter(R.string.skip))
			} else {
				emptyList()
			}
			result.isEmpty() -> listOf(
				when {
					loading -> LoadingState()
					source == null -> ButtonFooter(R.string.choose_source)
					else -> SourceReplacementEmptyState
				},
			)

			loading -> result + LoadingFooter()
			else -> result
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	init {
		buildReplacementPlan()
	}

	fun selectSource(source: MangaSource?) {
		if (isPlanning.value) {
			return
		}
		if (source?.name in originalSourceNames) {
			return
		}
		_isComparingSources.value = false
		sourceComparisonItems.value = emptyList()
		if (source == null) {
			_isManualMode.value = true
			_selectedSource.value = null
			activeMatches.value = emptyMap()
			results.value = emptyList()
			updateMatchedCount()
			return
		}
		_selectedSource.value = source
		if (isManualMode.value) {
			manualTriedSourceNames += source.name
			activeMatches.value = emptyMap()
			updateMatchedCount()
			startSearch()
		} else {
			resolveSource(source)
		}
	}

	fun retry() {
		if (selectedSource.value == null) {
			showSourceComparison()
		} else {
			startSearch()
		}
	}

	fun search(query: String) {
		if (!isManualMode.value || selectedSource.value == null) {
			return
		}
		val normalizedQuery = query.trim()
		if (normalizedQuery.isNotEmpty()) {
			startSearch(normalizedQuery)
		}
	}

	fun selectNextSource() {
		if (isPlanning.value || !isManualMode.value) {
			return
		}
		val next = manualSourceOrder.firstOrNull { it.name !in manualTriedSourceNames }
		if (next == null) {
			showSourceComparison()
		} else {
			selectSource(next)
		}
	}

	fun skipCurrent() {
		if (isPlanning.value || migrationJob?.isActive == true || mangaQueue.isEmpty()) {
			return
		}
		planningJob?.cancel()
		searchJob?.cancel()
		val skipped = manga
		mangaQueue = mangaQueue.filterNot { it.id == skipped.id }
		_selectedSource.value = null
		results.value = emptyList()
		activeMatches.value = emptyMap()
		manualSourceOrder = emptyList()
		manualTriedSourceNames.clear()
		_queueSize.value = mangaQueue.size
		updateMatchedCount()
		if (mangaQueue.isEmpty()) {
			_replacementSummary.value = createReplacementSummary()
			return
		}
		currentIndex = currentIndex.coerceAtMost(mangaQueue.lastIndex)
		savedStateHandle[AppRouter.KEY_INDEX] = currentIndex
		_currentManga.value = mangaQueue[currentIndex]
		_queuePosition.value = currentIndex + 1
		showSourceComparison()
	}

	fun skipRemaining() {
		if (isPlanning.value || migrationJob?.isActive == true || mangaQueue.isEmpty()) {
			return
		}
		planningJob?.cancel()
		searchJob?.cancel()
		mangaQueue = emptyList()
		_selectedSource.value = null
		_isComparingSources.value = false
		results.value = emptyList()
		sourceComparisonItems.value = emptyList()
		activeMatches.value = emptyMap()
		manualSourceOrder = emptyList()
		manualTriedSourceNames.clear()
		_queueSize.value = 0
		updateMatchedCount()
		_replacementSummary.value = createReplacementSummary()
	}

	fun selectSourceScope(sourceScope: AlternativeSourceScope) {
		if (sourceScope == _sourceScope.value || scopeOptions.value?.isAvailable(sourceScope) != true) {
			return
		}
		sourceScopeInitialized = true
		_sourceScope.value = sourceScope
		_selectedSource.value = null
		_isComparingSources.value = false
		_isManualMode.value = false
		sourceComparisonItems.value = emptyList()
		activeMatches.value = emptyMap()
		results.value = emptyList()
		updateMatchedCount()
		buildReplacementPlan()
	}

	fun showSourceComparison() {
		if (isPlanning.value) {
			return
		}
		searchJob?.cancel()
		_isComparingSources.value = true
		sourceComparisonItems.value = emptyList()
		searchJob = launchLoadingJob(Dispatchers.IO) {
			val manual = isManualMode.value
			val choices = getAvailableSources().let { available ->
				if (manual) available.sortedBy { it.source.name } else available
			}
			val selectedName = selectedSource.value?.name
			if (manual) {
				manualSourceOrder = choices.map(SourceChoice::source)
			}
			sourceComparisonItems.value = choices.map { choice ->
				SourceReplacementModel(
					source = choice.source,
					matched = choice.matched,
					total = choice.total,
					matchPercent = choice.matchPercent,
					isSelected = choice.source.name == selectedName,
				)
			} + SourceReplacementModel(
				source = null,
				matched = 0,
				total = 0,
				matchPercent = 0,
				isSelected = false,
			)
		}
	}

	private suspend fun getAvailableSources(): List<SourceChoice> {
		val excludedNames = originalSourceNames + usedSourceNames + failedSourceNames
		val ranked = replacementPlans.value.filter { it.source.name !in excludedNames }.map { plan ->
			SourceChoice(
				source = plan.source,
				matched = plan.matchedCount,
				total = plan.total,
				averageTitleSimilarity = plan.averageTitleSimilarity,
			)
		}.sortedWith(
			compareByDescending<SourceChoice>(SourceChoice::matched)
				.thenByDescending(SourceChoice::averageTitleSimilarity),
		)
		val rankedSourceNames = ranked.mapTo(hashSetOf()) { it.source.name }
		val sampleSize = replacementPlans.value.firstOrNull()?.total ?: mangaQueue.size.coerceAtMost(25)
		val other = alternativesUseCase.getCandidateSources(
			ref = originSource,
			sourceScope = sourceScope.value,
			sameLanguageOnly = true,
		)
			.filter { it.name !in excludedNames && it.name !in rankedSourceNames }
			.sortedBy(MangaSource::name)
			.map { SourceChoice(it, matched = 0, total = sampleSize, averageTitleSimilarity = 0f) }
		return ranked + other
	}

	fun migrate(target: Manga) {
		if (migrationJob?.isActive == true) {
			return
		}
		val seed = manga
		migrationJob = launchLoadingJob(Dispatchers.IO) {
			migrateUseCase(seed, target)
			recordReplacement(target.source)
			mangaQueue = mangaQueue.filterNot { it.id == seed.id }
			val remaining = mangaQueue.size
			if (remaining > 0) {
				moveToFirstRemaining()
			}
			_queueSize.value = remaining
			updateMatchedCount()
			if (remaining > 0) {
				_selectedSource.value = null
				results.value = emptyList()
				showSourceComparison()
			}
			onMigrated.call(MigrationResult(target, remaining))
			if (remaining == 0) {
				_replacementSummary.value = createReplacementSummary()
			}
		}
	}

	private fun buildReplacementPlan() {
		planningJob?.cancel()
		_isPlanning.value = true
		_planningStep.value = SourceReplacementStep.COMPARING_SOURCES
		_planningProgress.value = 0
		_planningTotal.value = 0
		_planningSourcesRemaining.value = 0
		_planningSampleSize.value = 0
		_planningCurrentSource.value = null
		results.value = emptyList()
		planningJob = launchLoadingJob(Dispatchers.IO) {
			var ready: PlanReady? = null
			try {
				val scopeOptions = alternativesUseCase.getSourceScopeOptions(
					ref = originSource,
					sameLanguageOnly = true,
				)
				_scopeOptions.value = scopeOptions
				// Fall back to the default when the chosen scope went away, e.g. its preset was deleted.
				if (!sourceScopeInitialized || !scopeOptions.isAvailable(_sourceScope.value)) {
					_sourceScope.value = scopeOptions.defaultScope
					sourceScopeInitialized = true
				}
				val sourceManga = mangaDataRepository.findAllMangaBySource(originSource)
				if (!queueInitialized) {
					val libraryManga = mangaDataRepository.findLibraryMangaBySource(originSource)
					mangaQueue = (requestedManga.filter { it.source.name == originSource.name } + libraryManga)
						.distinctBy(Manga::id)
					queueInitialized = true
				}
				_replacementSummary.value = null
				val initial = requestedManga.first { it.source.name == originSource.name }
				currentIndex = mangaQueue.indexOfFirst { it.id == initial.id }.coerceAtLeast(0)
				_currentManga.value = mangaQueue[currentIndex]
				_queueSize.value = mangaQueue.size
				_queuePosition.value = currentIndex + 1
				val comparisonPool = (mangaQueue + sourceManga).distinctBy(Manga::id)
				val plans = replacementPlanner(
					manga = comparisonPool,
					sourceScope = sourceScope.value,
					excludedSourceNames = originalSourceNames + usedSourceNames + failedSourceNames,
					onProgress = { progress ->
						_planningProgress.value = progress.completedChecks
						_planningTotal.value = progress.totalChecks
						_planningSourcesRemaining.value = progress.remainingSources
						_planningSampleSize.value = progress.sampleSize
					},
					onSourceFailed = { failedSourceNames += it.name },
				)
				val availablePlans = plans.filter { it.source.name !in usedSourceNames && it.source.name !in failedSourceNames }
				replacementPlans.value = availablePlans
				val best = availablePlans.firstOrNull()
				_selectedSource.value = best?.source
				activeMatches.value = emptyMap()
				updateMatchedCount()
				ready = PlanReady(best?.matchedCount ?: 0, mangaQueue.size)
			} finally {
				_isPlanning.value = false
			}
			ready?.let(onPlanReady::call)
		}
	}

	private fun resolveSource(source: MangaSource) {
		planningJob?.cancel()
		searchJob?.cancel()
		results.value = emptyList()
		activeMatches.value = emptyMap()
		updateMatchedCount()
		_isPlanning.value = true
		_planningStep.value = SourceReplacementStep.MATCHING_TITLES
		_planningProgress.value = 0
		_planningTotal.value = mangaQueue.size
		_planningSourcesRemaining.value = 0
		_planningSampleSize.value = mangaQueue.size
		_planningCurrentSource.value = source
		planningJob = launchLoadingJob(Dispatchers.IO) {
			var showManual = false
			try {
				val resolved = replacementPlanner.resolve(mangaQueue, source) { completed, total ->
					_planningProgress.value = completed
					_planningTotal.value = total
				}
				if (selectedSource.value == source && !isManualMode.value) {
					val combinedMatches = resolved.toMutableMap()
					var unmatched = mangaQueue.filterNot { it.id in combinedMatches }
					updateMatchedCount()
					usedSourceNames += source.name
					val mixSources = replacementPlans.value.map(SourceReplacementPlan::source)
						.filter { it.name !in usedSourceNames && it.name !in originalSourceNames }
						.distinctBy(MangaSource::name)
					_planningStep.value = SourceReplacementStep.SMART_MIX
					for ((index, mixSource) in mixSources.withIndex()) {
						if (unmatched.isEmpty()) {
							break
						}
						_planningCurrentSource.value = mixSource
						_planningProgress.value = 0
						_planningTotal.value = unmatched.size
						_planningSourcesRemaining.value = mixSources.size - index
						val mixed = replacementPlanner.resolve(unmatched, mixSource) { completed, total ->
							_planningProgress.value = completed
							_planningTotal.value = total
						}
						usedSourceNames += mixSource.name
						combinedMatches.putAll(mixed)
						unmatched = unmatched.filterNot { it.id in mixed }
					}
					activeMatches.value = combinedMatches
					updateMatchedCount()
					val replacements = getMatches()
					_planningStep.value = SourceReplacementStep.APPLYING_MATCHES
					_planningCurrentSource.value = null
					_planningProgress.value = 0
					_planningTotal.value = replacements.size
					// Each migration is its own transaction: a failure (or cancellation) leaves the titles
					// already moved in place and the rest untouched, still in the queue.
					val replacedIds = HashSet<Long>(replacements.size)
					var firstError: Throwable? = null
					try {
						replacements.forEachIndexed { index, (seed, target) ->
							runCatchingCancellable {
								migrateUseCase(seed, target)
							}.onSuccess {
								replacedIds += seed.id
								recordReplacement(target.source)
							}.onFailure { e ->
								if (firstError == null) firstError = e
							}
							_planningProgress.value = index + 1
						}
					} finally {
						mangaQueue = mangaQueue.filterNot { it.id in replacedIds }
					}
					firstError?.let { errorEvent.call(it) }
					val remaining = mangaQueue.size
					if (remaining > 0) {
						moveToFirstRemaining()
					}
					_queueSize.value = remaining
					activeMatches.value = emptyMap()
					_isManualMode.value = remaining > 0
					_selectedSource.value = null
					updateMatchedCount()
					showManual = remaining > 0
					onBatchMigrated.call(BatchMigrationResult(replacedIds.size, remaining))
					if (remaining == 0) {
						_replacementSummary.value = createReplacementSummary()
					}
				}
			} finally {
				_isPlanning.value = false
			}
			if (showManual) {
				showSourceComparison()
			}
		}
	}

	fun cancelPendingChecks() {
		planningJob?.cancel()
		searchJob?.cancel()
	}

	private fun moveToFirstRemaining() {
		currentIndex = 0
		savedStateHandle[AppRouter.KEY_INDEX] = currentIndex
		val next = mangaQueue.first()
		_queuePosition.value = 1
		_currentManga.value = next
		manualSourceOrder = emptyList()
		manualTriedSourceNames.clear()
	}

	private fun updateMatchedCount() {
		_matchedCount.value = if (selectedSource.value == null) 0 else getMatches().size
	}

	private fun recordReplacement(source: MangaSource) {
		replacementCount++
		val previous = replacementSourceUsage[source.name]
		replacementSourceUsage[source.name] = SourceUsage(source, (previous?.count ?: 0) + 1)
	}

	private fun createReplacementSummary() = ReplacementSummary(
		replaced = replacementCount,
		sources = replacementSourceUsage.values.toList(),
	)

	private fun getMatches(): List<Pair<Manga, Manga>> {
		val resolved = activeMatches.value
		return mangaQueue.mapNotNull { seed ->
			resolved[seed.id]?.let { target -> seed to target }
		}
	}

	private fun startSearch(query: String = manga.title) {
		val seed = manga
		val source = selectedSource.value ?: run {
			results.value = emptyList()
			return
		}
		searchJob?.cancel()
		results.value = emptyList()
		searchJob = launchLoadingJob(Dispatchers.IO) {
			val reference = runCatchingCancellable {
				mangaRepositoryFactory.create(seed.source).getDetails(seed)
			}.getOrDefault(seed)
			val candidates = alternativesUseCase.searchSource(reference, source, query)
			candidates.collect { candidate ->
				if (manga.id != seed.id || selectedSource.value != source) {
					return@collect
				}
				val model = candidate.toAlternativeModel(reference)
				results.update { current ->
					if (current.any { it.manga.id == candidate.id }) current else current + model
				}
			}
		}
	}

	private suspend fun Manga.toAlternativeModel(reference: Manga) = MangaAlternativeModel(
		mangaModel = mangaListMapper.toListModel(this, ListMode.GRID) as MangaGridModel,
		referenceChapters = reference.chaptersCount(),
	)

	data class SourceChoice(
		val source: MangaSource,
		val matched: Int,
		val total: Int,
		val averageTitleSimilarity: Float,
	) {
		val matchPercent: Int
			get() = if (total == 0) 0 else matched * 100 / total
	}

	data class PlanReady(
		val matched: Int,
		val total: Int,
	)

	data class MigrationResult(
		val target: Manga,
		val remaining: Int,
	)

	data class BatchMigrationResult(
		val replaced: Int,
		val remaining: Int,
	)

	data class SourceUsage(
		val source: MangaSource,
		val count: Int,
	)

	data class ReplacementSummary(
		val replaced: Int,
		val sources: List<SourceUsage>,
	)

}

enum class SourceReplacementStep {
	COMPARING_SOURCES,
	MATCHING_TITLES,
	SMART_MIX,
	APPLYING_MATCHES,
}

private data class ReplacementScreenState(
	val planning: Boolean,
	val comparing: Boolean,
	val sourceItems: List<SourceReplacementModel>,
)
