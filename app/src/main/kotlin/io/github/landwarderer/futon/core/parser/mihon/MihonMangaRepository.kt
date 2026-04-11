package io.github.landwarderer.futon.core.parser.mihon

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.core.parser.mihon.loader.ChildFirstPathClassLoader
import io.github.landwarderer.futon.core.parser.mihon.loader.MihonModule
import io.github.landwarderer.futon.core.parser.mihon.model.MihonMangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * A repository that delegates calls to a Mihon (Tachiyomi) extension.
 * It handles the initialization of the extension's environment and maps
 * its data models to Futon's models.
 */
class MihonMangaRepository(
	override val source: MihonMangaSource,
	private val mihonModule: MihonModule,
	cache: MemoryContentCache
) : CachingMangaRepository(cache) {

	private var internalSource: Any? = null

	override val sortOrders: Set<SortOrder>
		get() = setOf(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.RELEVANCE)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	@Suppress("OPT_IN_USAGE")
	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = false
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return withContext(Dispatchers.IO) {
			try {
				val src = getInternalSource()
				val getFilterList = src.javaClass.getMethod("getFilterList")
				val filterList = getFilterList.invoke(src) as List<*>
				
				val tags = mutableSetOf<MangaTag>()
				for (filter in filterList) {
					if (filter == null) continue
					// Handle Filter.Tag and Filter.Group which often contain tags
					if (filter.javaClass.name.endsWith(".Filter\$Tag") || filter.javaClass.name.endsWith(".Filter\$CheckBox")) {
						val name = filter.javaClass.getMethod("getName").invoke(filter) as String
						tags.add(MangaTag(name, name, source))
					} else if (filter.javaClass.name.endsWith(".Filter\$Group")) {
						val state = filter.javaClass.getMethod("getState").invoke(filter) as List<*>
						for (subFilter in state) {
							if (subFilter == null) continue
							val name = subFilter.javaClass.getMethod("getName").invoke(subFilter) as String
							tags.add(MangaTag(name, name, source))
						}
					}
				}
				
				if (tags.isEmpty()) return@withContext MangaListFilterOptions()
				
				@Suppress("OPT_IN_USAGE")
				MangaListFilterOptions(availableTags = tags)
			} catch (e: Exception) {
				MangaListFilterOptions()
			}
		}
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> =
		withContext(Dispatchers.IO) {
			val src = getInternalSource()
			val page = (offset / 20) + 1 // Mihon usually uses 1-based page index

			val observable = if (filter?.query?.isNotEmpty() == true) {
				val fetchSearchManga = src.javaClass.getMethod("fetchSearchManga", Int::class.java, String::class.java, Any::class.java)
				fetchSearchManga.invoke(src, page, filter.query, getEmptyFilterList(src))
			} else if (order == SortOrder.UPDATED) {
				val fetchLatestUpdates = src.javaClass.getMethod("fetchLatestUpdates", Int::class.java)
				fetchLatestUpdates.invoke(src, page)
			} else {
				val fetchPopularManga = src.javaClass.getMethod("fetchPopularManga", Int::class.java)
				fetchPopularManga.invoke(src, page)
			}

			val mangapage = observable.javaClass.getMethod("toBlocking").invoke(observable)
				.javaClass.getMethod("first").invoke(observable.javaClass.getMethod("toBlocking").invoke(observable))
			
			val mangas = mangapage.javaClass.getField("mangas").get(mangapage) as List<*>
			mangas.map { MihonDataConverters.toFutonManga(it!!, source) }
		}

	override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
		val src = getInternalSource()
		val classLoader = src.javaClass.classLoader!!
		val mihonManga = classLoader.loadClass("eu.kanade.tachiyomi.source.model.SManga").getDeclaredConstructor().newInstance()
		mihonManga.javaClass.getMethod("setUrl", String::class.java).invoke(mihonManga, manga.url)

		val fetchMangaDetails = src.javaClass.getMethod("fetchMangaDetails", mihonManga.javaClass)
		val observableManga = fetchMangaDetails.invoke(src, mihonManga)
		val detailedMihonManga = observableManga.javaClass.getMethod("toBlocking").invoke(observableManga)
			.javaClass.getMethod("first").invoke(observableManga.javaClass.getMethod("toBlocking").invoke(observableManga))

		val fetchChapterList = src.javaClass.getMethod("fetchChapterList", mihonManga.javaClass)
		val observableChapters = fetchChapterList.invoke(src, mihonManga)
		val mihonChapters = observableChapters.javaClass.getMethod("toBlocking").invoke(observableChapters)
			.javaClass.getMethod("first").invoke(observableChapters.javaClass.getMethod("toBlocking").invoke(observableChapters)) as List<*>

		MihonDataConverters.toFutonManga(detailedMihonManga!!, source).copy(
			id = manga.id, // Keep original ID
			chapters = mihonChapters.map { MihonDataConverters.toFutonChapter(it!!, source) }
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
		val src = getInternalSource()
		val classLoader = src.javaClass.classLoader!!
		val mihonChapter = classLoader.loadClass("eu.kanade.tachiyomi.source.model.SChapter").getDeclaredConstructor().newInstance()
		mihonChapter.javaClass.getMethod("setUrl", String::class.java).invoke(mihonChapter, chapter.url)

		val fetchPageList = src.javaClass.getMethod("fetchPageList", mihonChapter.javaClass)
		val observable = fetchPageList.invoke(src, mihonChapter)
		val pages = observable.javaClass.getMethod("toBlocking").invoke(observable)
			.javaClass.getMethod("first").invoke(observable.javaClass.getMethod("toBlocking").invoke(observable)) as List<*>

		pages.map { MihonDataConverters.toFutonPage(it!!, source) }
	}

	override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
		// If URL is already an image (usually true for simple sources), return it
		if (page.url.endsWith(".jpg") || page.url.endsWith(".png") || page.url.endsWith(".webp")) {
			return@withContext page.url
		}
		
		val src = getInternalSource()
		val classLoader = src.javaClass.classLoader!!
		val mihonPage = classLoader.loadClass("eu.kanade.tachiyomi.source.model.Page")
			.getConstructor(Int::class.java, String::class.java, String::class.java)
			.newInstance(page.id.toInt(), "", page.url)
		
		val fetchImageUrl = src.javaClass.getMethod("fetchImageUrl", mihonPage.javaClass)
		val observable = fetchImageUrl.invoke(src, mihonPage)
		val imageUrl = observable.javaClass.getMethod("toBlocking").invoke(observable)
			.javaClass.getMethod("first").invoke(observable.javaClass.getMethod("toBlocking").invoke(observable)) as String
		
		imageUrl
	}

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	private fun getInternalSource(): Any {
		internalSource?.let { return it }
		synchronized(this) {
			internalSource?.let { return it }
			val pkgInfo = mihonModule.application.packageManager.getPackageInfo(source.packageName, 0)
			val appInfo = pkgInfo.applicationInfo!!
			val dexPath = buildString {
				append(appInfo.sourceDir)
				appInfo.splitSourceDirs?.forEach {
					append(java.io.File.pathSeparator)
					append(it)
				}
			}
			val classLoader = ChildFirstPathClassLoader(
				dexPath,
				appInfo.nativeLibraryDir,
				mihonModule.application.classLoader
			)

			val sourceClass = if (source.factoryClassName != null) {
				val factoryClass = classLoader.loadClass(source.factoryClassName)
				val factory = factoryClass.getDeclaredConstructor().newInstance()
				val createSources = factoryClass.getMethod("createSources")
				val sources = createSources.invoke(factory) as List<*>
				return sources.first { it!!.javaClass.name == source.className }!!
			} else {
				classLoader.loadClass(source.className)
			}

			val instance = try {
				sourceClass.getDeclaredConstructor().newInstance()
			} catch (e: Exception) {
				// Some sources might have different constructor patterns, but usually it's empty
				sourceClass.getConstructor().newInstance()
			}

			// Initialize HttpSource if applicable
			try {
				val setClient = sourceClass.getMethod("setClient", mihonModule.httpClient.javaClass)
				setClient.invoke(instance, mihonModule.httpClient)
			} catch (e: Exception) {}

			internalSource = instance
			return instance
		}
	}

	private fun getEmptyFilterList(src: Any): Any {
		val classLoader = src.javaClass.classLoader!!
		val filterListClass = classLoader.loadClass("eu.kanade.tachiyomi.source.model.FilterList")
		return filterListClass.getConstructor(List::class.java).newInstance(emptyList<Any>())
	}
}
