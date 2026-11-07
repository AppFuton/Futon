package io.github.landwarderer.futon.alternatives.domain

import io.github.landwarderer.futon.core.model.contentTypeCompat
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.model.primaryLanguage
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.parser.ParserMangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.data.SourcePreset
import io.github.landwarderer.futon.explore.data.SourcePresetsRepository
import io.github.landwarderer.futon.search.domain.SearchKind
import io.github.landwarderer.futon.search.domain.SearchV2Helper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.github.landwarderer.futon.core.model.isNovelSource
import io.github.landwarderer.futon.core.util.ext.toLocale
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val MAX_PARALLELISM = 8
private const val MAX_PER_SOURCE_PARALLELISM = 2

class AlternativesUseCase @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
	private val presetsRepository: SourcePresetsRepository,
) {

	private val requestSemaphore = Semaphore(MAX_PARALLELISM)
	private val webViewSemaphore = Semaphore(1)
	private val sourceSemaphores = HashMap<String, Semaphore>()
	private val webViewSources = HashMap<String, Boolean>()

	/** Searches the enabled or the disabled sources, in any language. */
	suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
		val sources = getCandidateSources(
			ref = manga.source,
			sourceScope = if (throughDisabledSources) AlternativeSourceScope.Disabled else AlternativeSourceScope.Enabled,
			sameLanguageOnly = false,
		)
		return search(manga, sources, manga.title, loadDetails = true)
	}

	suspend operator fun invoke(
		manga: Manga,
		sourceScope: AlternativeSourceScope,
		query: String = manga.title,
		loadDetails: Boolean = true,
	): Flow<Manga> {
		val sources = getCandidateSources(
			ref = manga.source,
			sourceScope = sourceScope,
			sameLanguageOnly = true,
		)
		return search(manga, sources, query, loadDetails)
	}

	/**
	 * @param loadDetails fetch each candidate's details page. Needed for chapter counts, but a caller
	 * that only wants what the search already returned - a cover, say - should pass `false`: it is one
	 * extra network round trip per result.
	 */
	suspend operator fun invoke(
		manga: Manga,
		options: AlternativesSearchOptions,
		loadDetails: Boolean = true,
	): Flow<AlternativeSearchEvent> {
		val query = options.query.trim()
		if (query.isEmpty()) return emptyFlow()
		val sources = getCandidateSources(
			ref = manga.source,
			sourceScope = options.sourceScope,
			sameLanguageOnly = options.sameLanguageOnly,
			sameContentTypeOnly = options.sameContentTypeOnly,
		)
		if (sources.isEmpty()) return emptyFlow()
		return channelFlow {
			val completedSources = AtomicInteger()
			send(AlternativeSearchEvent.Progress(0, sources.size))
			val workerCount = minOf(MAX_PARALLELISM, sources.size)
			repeat(workerCount) { workerIndex ->
				launch {
					for (sourceIndex in workerIndex until sources.size step workerCount) {
						val source = sources[sourceIndex]
						try {
							// Finish this source's candidate details before this worker starts another
							// source, so chapter requests are not starved behind every source search.
							coroutineScope {
								search(
									manga = manga,
									sources = listOf(source),
									query = query,
									loadDetails = false,
								).collect { candidate ->
									launch {
										send(
											AlternativeSearchEvent.Result(
												if (loadDetails) loadDetails(candidate) else candidate,
											),
										)
									}
								}
							}
						} finally {
							send(
								AlternativeSearchEvent.Progress(
									completedSources = completedSources.incrementAndGet(),
									totalSources = sources.size,
								),
							)
						}
					}
				}
			}
		}
	}

	suspend fun getCandidateSources(
		ref: MangaSource,
		sourceScope: AlternativeSourceScope,
		sameLanguageOnly: Boolean = true,
		sameContentTypeOnly: Boolean = false,
	): List<MangaSource> {
		val sources = when (sourceScope) {
			is AlternativeSourceScope.Preset -> presetsRepository.getById(sourceScope.id)
				?.let { getPresetSources(it) }
				.orEmpty()

			AlternativeSourceScope.Enabled -> sourcesRepository.getEnabledSources()
			AlternativeSourceScope.Disabled -> sourcesRepository.getDisabledSources().toList()
		}
		val skipNsfw = settings.isNsfwContentDisabled
		return sources.asSequence()
			.distinctBy(MangaSource::name)
			.filter { it.name != ref.name }
			.filter { it.isNovelSource == ref.isNovelSource }
			.filterNot { skipNsfw && it.isNsfw() }
			.filter { !sameLanguageOnly || it.hasSameLanguageAs(ref) }
			.filter { !sameContentTypeOnly || it.hasSameContentTypeAs(ref) }
			.sortedWith(compareByDescending<MangaSource> { it.priority(ref) }.thenBy(MangaSource::name))
			.toList()
	}

	suspend fun getSourceScopeOptions(
		ref: MangaSource,
		sameLanguageOnly: Boolean = false,
	): AlternativeSourceScopeOptions {
		val skipNsfw = settings.isNsfwContentDisabled
		fun MangaSource.isCandidate() = name != ref.name &&
			isNovelSource == ref.isNovelSource &&
			(!skipNsfw || !isNsfw()) &&
			(!sameLanguageOnly || hasSameLanguageAs(ref))

		// A preset with nothing searchable for this manga would only ever produce an empty screen.
		val presets = presetsRepository.getAll()
			.filter { preset -> getPresetSources(preset).any { it.isCandidate() } }
			.map { AlternativeSourceScopeOptions.PresetChoice(it.id, it.title) }
		val activePresetId = settings.activeSourcePresetId
		return AlternativeSourceScopeOptions(
			// The active preset when it has candidates, otherwise the enabled sources.
			defaultScope = if (presets.any { it.id == activePresetId }) {
				AlternativeSourceScope.Preset(activePresetId)
			} else {
				AlternativeSourceScope.Enabled
			},
			presets = presets,
			hasDisabledSources = sourcesRepository.getDisabledSources().any { it.isCandidate() },
		)
	}

	suspend fun searchSource(
		manga: Manga,
		source: MangaSource,
		query: String = manga.title,
		loadDetails: Boolean = true,
		onFailure: (() -> Unit)? = null,
	): Flow<Manga> = if (source.name == manga.source.name) {
		emptyFlow()
	} else {
		search(
			manga = manga,
			sources = listOf(source),
			query = query,
			loadDetails = loadDetails,
			onSourceFailure = { onFailure?.invoke() },
		)
	}

	fun isWebViewSource(source: MangaSource): Boolean = source.usesWebView()

	private fun search(
		manga: Manga,
		sources: List<MangaSource>,
		query: String,
		loadDetails: Boolean,
		onSourceFailure: (MangaSource) -> Unit = {},
	): Flow<Manga> {
		if (sources.isEmpty()) {
			return emptyFlow()
		}
		return channelFlow {
			for (source in sources) {
				launch {
					val searchHelper = searchHelperFactory.create(source)
					val searchResult = runCatchingCancellable {
						withSourcePermit(source) {
							searchHelper(query, SearchKind.TITLE)?.manga
						}
					}
					if (searchResult.isFailure) {
						onSourceFailure(source)
					}
					searchResult.getOrNull()?.forEach { m ->
						if (m.id != manga.id) {
							if (loadDetails) {
								launch {
									send(loadDetails(m))
								}
							} else {
								send(m)
							}
						}
					}
				}
			}
		}
	}

	private suspend fun loadDetails(manga: Manga): Manga = runCatchingCancellable {
		withSourcePermit(manga.source) {
			mangaRepositoryFactory.create(manga.source).getDetails(manga)
		}
	}.getOrDefault(manga)

	private suspend fun <T> withSourcePermit(source: MangaSource, block: suspend () -> T): T {
		return source.requestSemaphore().withPermit {
			if (source.usesWebView()) {
				webViewSemaphore.withPermit {
					requestSemaphore.withPermit { block() }
				}
			} else {
				requestSemaphore.withPermit { block() }
			}
		}
	}

	private fun MangaSource.requestSemaphore(): Semaphore = synchronized(sourceSemaphores) {
		sourceSemaphores.getOrPut(name) {
			Semaphore(if (usesWebView()) 1 else MAX_PER_SOURCE_PARALLELISM)
		}
	}

	/** Sources that load through a WebView cannot run in parallel without starving each other. */
	private fun MangaSource.usesWebView(): Boolean = synchronized(webViewSources) {
		webViewSources.getOrPut(name) {
			val repository = mangaRepositoryFactory.create(this)
			(repository as? ParserMangaRepository)
				?.getConfigKeys()
				?.filterIsInstance<ConfigKey.DisableUpdateChecking>()
				?.any { it.defaultValue } == true
		}
	}

	private suspend fun getPresetSources(preset: SourcePreset): List<MangaSource> {
		return sourcesRepository.getPresetSources(preset)
	}

	private fun MangaSource.hasSameLanguageAs(ref: MangaSource): Boolean {
		val refLanguage = ref.primaryLanguage ?: return true
		// Multilingual sources (and Mihon "all" extensions) may well carry the title in that language.
		val language = primaryLanguage ?: return true
		return language == refLanguage
	}

	private fun MangaSource.hasSameContentTypeAs(ref: MangaSource): Boolean {
		val type = contentTypeCompat ?: return false
		return type == ref.contentTypeCompat
	}

	private fun MangaSource.priority(ref: MangaSource): Int {
		var res = 0
		val language = primaryLanguage
		if (language != null) {
			if (language == ref.primaryLanguage) {
				res += 4
			} else if (language == Locale.getDefault().language) {
				res += 2
			}
		}
		val type = contentTypeCompat
		if (type != null && type == ref.contentTypeCompat) {
			res++
		}
		return res
	}
}
