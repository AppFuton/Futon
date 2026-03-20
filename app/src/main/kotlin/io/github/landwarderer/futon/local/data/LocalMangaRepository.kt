package io.github.landwarderer.futon.local.data

import androidx.core.net.toFile
import androidx.core.net.toUri
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.core.model.isLocal
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.AlphanumComparator
import io.github.landwarderer.futon.core.util.ext.deleteAwait
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.takeIfWriteable
import io.github.landwarderer.futon.core.util.ext.withChildren
import io.github.landwarderer.futon.local.data.index.LocalMangaIndex
import io.github.landwarderer.futon.local.data.input.LocalMangaParser
import io.github.landwarderer.futon.local.data.output.LocalMangaOutput
import io.github.landwarderer.futon.local.data.output.LocalMangaUtil
import io.github.landwarderer.futon.local.domain.MangaLock
import io.github.landwarderer.futon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.levenshteinDistance
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_PARALLELISM = 4
private const val FILENAME_SKIP = ".notamanga"

@Singleton
class LocalMangaRepository @Inject constructor(
	private val storageManager: LocalStorageManager,
	private val localMangaIndex: LocalMangaIndex,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val settings: AppSettings,
	private val lock: MangaLock,
) : MangaRepository {

	override val source = LocalMangaSource

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder
		get() = settings.localListOrder
		set(value) {
			settings.localListOrder = value
		}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = localMangaIndex.getAvailableTags(
			skipNsfw = settings.isNsfwContentDisabled,
		).mapToSet { MangaTag(title = it, key = it, source = source) },
		availableContentRating = if (!settings.isNsfwContentDisabled) {
			EnumSet.of(ContentRating.SAFE, ContentRating.ADULT)
		} else {
			emptySet()
		},
	)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		val list = getRawList()
		if (settings.isNsfwContentDisabled) {
			list.removeAll { it.manga.isNsfw() }
		}
		if (filter != null) {
			val query = filter.query
			if (!query.isNullOrEmpty()) {
				list.retainAll { x -> x.isMatchesQuery(query) }
			}
			if (filter.tags.isNotEmpty()) {
				list.retainAll { x -> x.containsTags(filter.tags.mapToSet { it.title }) }
			}
			if (filter.tagsExclude.isNotEmpty()) {
				list.removeAll { x -> x.containsAnyTag(filter.tagsExclude.mapToSet { it.title }) }
			}
			filter.contentRating.singleOrNull()?.let { contentRating ->
				val isNsfw = contentRating == ContentRating.ADULT
				list.retainAll { it.manga.isNsfw() == isNsfw }
			}
			if (!query.isNullOrEmpty() && order == SortOrder.RELEVANCE) {
				list.sortBy { it.manga.title.levenshteinDistance(query) }
			}
		}
		when (order) {
			SortOrder.ALPHABETICAL -> list.sortWith(compareBy(AlphanumComparator()) { x -> x.manga.title })
			SortOrder.RATING -> list.sortByDescending { it.manga.rating }
			SortOrder.NEWEST,
			SortOrder.UPDATED -> list.sortWith(compareBy({ -it.createdAt }, { it.manga.id }))

			else -> Unit
		}
		return list.unwrap()
	}

	override suspend fun getDetails(manga: Manga): Manga = when {
		!manga.isLocal -> requireNotNull(findSavedManga(manga, withDetails = true)?.manga) {
			"Manga is not local or saved"
		}

		else -> LocalMangaParser(manga.url.toUri()).getManga(withDetails = true).manga
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return LocalMangaParser(chapter.url.toUri()).getPages(chapter)
	}

	suspend fun delete(manga: Manga): Boolean {
		val file = manga.url.toUri().toFile()
		val result = file.deleteAwait()
		if (result) {
			localMangaIndex.delete(manga.id)
			localStorageChanges.emit(null)
		}
		return result
	}

	suspend fun deleteChapters(manga: Manga, ids: Set<Long>) = lock.withLock(manga) {
		val subject = if (manga.isLocal) manga else checkNotNull(findSavedManga(manga, withDetails = false)) {
			"Manga is not stored on local storage"
		}.manga
		LocalMangaUtil(subject).deleteChapters(ids)
		val updated = getDetails(subject)
		localStorageChanges.emit(LocalManga(updated))
	}

	suspend fun getRemoteManga(localManga: Manga): Manga? {
		return runCatchingCancellable {
			LocalMangaParser(localManga.url.toUri()).getMangaInfo()?.takeUnless { it.isLocal }
		}.onFailure {
			it.printStackTraceDebug("LocalMangaRepository::getRemoteManga")
		}.getOrNull()
	}

	suspend fun findSavedManga(remoteManga: Manga, withDetails: Boolean = true): LocalManga? = runCatchingCancellable {
		// very fast path
		localMangaIndex.get(remoteManga.id, withDetails)?.let { cached ->
			return@runCatchingCancellable cached
		}
		// fast path
		LocalMangaParser.find(storageManager.getReadableDirs(), remoteManga)?.let {
			return it.getManga(withDetails)
		}
		// slow path
		val files = getAllFiles()
		return channelFlow {
			for (file in files) {
				launch {
					val mangaInput = LocalMangaParser.getOrNull(file)
					runCatchingCancellable {
						val mangaInfo = mangaInput?.getMangaInfo()
						if (mangaInfo != null && mangaInfo.id == remoteManga.id) {
							send(mangaInput)
						}
					}.onFailure {
						it.printStackTraceDebug("LocalMangaRepository::findSavedManga")
					}
				}
			}
		}.firstOrNull()?.getManga(withDetails)
	}.onSuccess { x: LocalManga? ->
		if (x != null) {
			localMangaIndex.put(x)
		}
	}.onFailure {
		it.printStackTraceDebug("LocalMangaRepository::findSavedManga")
	}.getOrNull()

	override suspend fun getPageUrl(page: MangaPage) = page.url

	override suspend fun getRelated(seed: Manga): List<Manga> = emptyList()

	suspend fun getOutputDir(manga: Manga, fallback: File?): File? {
		val defaultDir = fallback?.takeIfWriteable() ?: storageManager.getDefaultWriteableDir()
		if (defaultDir != null && LocalMangaOutput.get(defaultDir, manga) != null) {
			return defaultDir
		}
		return storageManager.getWriteableDirs()
			.firstOrNull {
				LocalMangaOutput.get(it, manga) != null
			} ?: defaultDir
	}

	suspend fun cleanup(): Boolean {
		if (lock.isNotEmpty()) {
			return false
		}
		val dirs = storageManager.getWriteableDirs()
		runInterruptible(Dispatchers.IO) {
			val filter = TempFileFilter()
			dirs.forEach { dir ->
				dir.withChildren { children ->
					children.forEach { child ->
						if (filter.accept(child)) {
							child.deleteRecursively()
						}
					}
				}
			}
		}
		return true
	}

	fun getRawListAsFlow(): Flow<LocalManga> = channelFlow {
		val files = getAllFiles()
		val dispatcher = Dispatchers.IO.limitedParallelism(MAX_PARALLELISM)
		for (file in files) {
			launch(dispatcher) {
				runCatchingCancellable {
					LocalMangaParser.getOrNull(file)?.getManga(withDetails = false)
				}.onFailure { e ->
					e.printStackTraceDebug("LocalMangaRepository::getRawListAsFlow")
				}.onSuccess { m ->
					if (m != null) send(m)
				}
			}
		}
	}

	private suspend fun getRawList(): ArrayList<LocalManga> = getRawListAsFlow().toCollection(ArrayList())

	private suspend fun getAllFiles() = storageManager.getReadableDirs()
		.asSequence()
		.flatMap { dir ->
			dir.withChildren { children -> children.filterNot { it.isHidden || it.shouldSkip() }.toList() }
		}

	private fun Collection<LocalManga>.unwrap(): List<Manga> = map { it.manga }

	private fun File.shouldSkip(): Boolean = isDirectory && File(this, FILENAME_SKIP).exists()
}
