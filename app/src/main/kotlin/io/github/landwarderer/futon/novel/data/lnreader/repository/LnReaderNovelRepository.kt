package io.github.landwarderer.futon.novel.data.lnreader.repository

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.longHashCode
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.runtime.LnPluginRuntime
import io.github.landwarderer.futon.novel.data.lnreader.runtime.LnPluginRuntimeClosedException
import io.github.landwarderer.futon.novel.data.lnreader.runtime.LnPluginRuntimeManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete implementation of MangaRepository that executes an LNReader JS plugin via QuickJS.
 */
class LnReaderNovelRepository(
	cache: MemoryContentCache,
	override val source: MangaSource,
	private val plugin: LnPlugin,
	private val runtimeManager: LnPluginRuntimeManager,
) : CachingMangaRepository(cache) {

	private suspend fun <T> withRuntime(block: suspend (LnPluginRuntime) -> T): T {
		val current = runtimeManager.getRuntime(plugin)
		return try {
			block(current)
		} catch (e: LnPluginRuntimeClosedException) {
			block(runtimeManager.getRuntime(plugin))
		}
	}

	override val sortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY)
	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = false,
	)

	private data class PagingKey(val query: String, val order: SortOrder?)
	private val paginators = ConcurrentHashMap<PagingKey, NovelPaginator>()

	private fun getPaginator(key: PagingKey, offset: Int): NovelPaginator {
		if (offset == 0) {
			val paginator = NovelPaginator(firstPage = 1)
			paginators[key] = paginator
			return paginator
		}
		return paginators.getOrPut(key) { NovelPaginator(firstPage = 1) }
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url
	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val query = filter?.query?.trim().orEmpty()
		val key = PagingKey(query, order)
		val paginator = getPaginator(key, offset)
		val page = paginator.getPage(offset)

		val items = withRuntime { currentRuntime ->
			if (query.isNotEmpty()) {
				currentRuntime.searchNovels(query, page)
			} else {
				currentRuntime.getPopularNovels(page)
			}
		}

		paginator.onListReceived(offset, page, items.size)

		return items.map { item ->
			val fullUrl = resolveUrl(item.path)
			Manga(
				id = "${plugin.id}_${item.path}".longHashCode(),
				title = item.name,
				altTitles = emptySet(),
				url = item.path,
				publicUrl = fullUrl,
				rating = 0f,
				contentRating = null,
				coverUrl = item.cover?.takeIf(String::isNotBlank)?.let(::resolveUrl).orEmpty(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = item.cover?.takeIf(String::isNotBlank)?.let(::resolveUrl),
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val novel = withRuntime { it.parseNovel(manga.url) }
		val chapters = novel.chapters.mapIndexed { index, chapterItem ->
			MangaChapter(
				id = "${plugin.id}_${chapterItem.path}".longHashCode(),
				title = chapterItem.name,
				number = chapterItem.chapterNumber ?: (index + 1).toFloat(),
				volume = 0,
				url = chapterItem.path,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}

		val tags = novel.genres?.split(",")
			?.map { it.trim() }
			?.filter { it.isNotEmpty() }
			?.map { MangaTag(title = it, key = it, source = source) }
			?.toSet()
			.orEmpty()

		return manga.copy(
			title = novel.name.ifBlank { manga.title },
			authors = novel.author?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet(),
			description = novel.summary,
			coverUrl = novel.cover?.takeIf(String::isNotBlank)?.let(::resolveUrl) ?: manga.coverUrl,
			largeCoverUrl = novel.cover?.takeIf(String::isNotBlank)?.let(::resolveUrl) ?: manga.largeCoverUrl,
			state = null,
			tags = tags,
			chapters = chapters,
		)
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val content = withRuntime { it.parseChapter(chapter.url) }
		val resolvedUrl = resolveUrl(chapter.url)
		val html = io.github.landwarderer.futon.novel.ui.reader.NovelChapterContent.toHtmlFragment(content, resolvedUrl)
		return listOf(
			MangaPage(
				id = chapter.id,
				url = html,
				preview = null,
				source = source,
			),
		)
	}

	private fun resolveUrl(path: String): String {
		if (path.startsWith("//")) {
			val scheme = runCatching { java.net.URI(plugin.site).scheme }.getOrNull() ?: "https"
			return "$scheme:$path"
		}
		if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(path)) return path
		return runCatching { java.net.URI(plugin.site).resolve(path).toString() }
			.getOrDefault(path)
	}
}

internal class NovelPaginator(private val firstPage: Int = 1) {
	private val pages = ConcurrentHashMap<Int, Int>()
	@Volatile private var nextPage: Int = firstPage

	fun getPage(offset: Int): Int {
		if (offset == 0) {
			nextPage = firstPage
			return firstPage
		}
		pages[offset]?.let { return it }
		// Fallback: If offset does not match an exact recorded boundary (e.g. because duplicate
		// items were removed by RemoteListViewModel), advance using the last known next page so
		// we never re-request the previous page and stall.
		return nextPage
	}

	fun onListReceived(offset: Int, page: Int, count: Int) {
		val next = if (count > 0) page + 1 else page
		nextPage = next
		pages[offset + count] = next
	}
}
