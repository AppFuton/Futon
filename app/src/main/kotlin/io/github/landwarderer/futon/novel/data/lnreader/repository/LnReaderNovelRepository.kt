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
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete implementation of MangaRepository that executes an LNReader JS plugin via QuickJS.
 */
class LnReaderNovelRepository(
	cache: MemoryContentCache,
	override val source: MangaSource,
	private val plugin: LnPlugin,
	private val runtime: LnPluginRuntime,
) : CachingMangaRepository(cache) {

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
			val paginator = NovelPaginator(initialPageSize = 20)
			paginators[key] = paginator
			return paginator
		}
		return paginators.getOrPut(key) { NovelPaginator(initialPageSize = 20) }
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url
	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val query = filter?.query?.trim().orEmpty()
		val key = PagingKey(query, order)
		val paginator = getPaginator(key, offset)
		val page = paginator.getPage(offset)

		val items = if (query.isNotEmpty()) {
			runtime.searchNovels(query, page)
		} else {
			runtime.getPopularNovels(page)
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
				coverUrl = item.cover.orEmpty(),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = item.cover,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	public override suspend fun getDetailsImpl(manga: Manga): Manga {
		val novel = runtime.parseNovel(manga.url)
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
			coverUrl = novel.cover ?: manga.coverUrl,
			largeCoverUrl = novel.cover ?: manga.largeCoverUrl,
			state = null,
			tags = tags,
			chapters = chapters,
		)
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val content = runtime.parseChapter(chapter.url)
		val resolvedUrl = resolveUrl(chapter.url)
		val html = io.github.landwarderer.futon.novel.ui.reader.NovelChapterContent.toHtmlFragment(content, resolvedUrl)
		return listOf(
			MangaPage(
				id = chapter.id,
				url = "novel_content://$html",
				preview = null,
				source = source,
			),
		)
	}

	private fun resolveUrl(path: String): String {
		if (path.startsWith("http://") || path.startsWith("https://")) return path
		val base = plugin.site.trimEnd('/')
		val cleanPath = if (path.startsWith("/")) path else "/$path"
		return base + cleanPath
	}
}

internal class NovelPaginator(private val initialPageSize: Int = 20) {
	var firstPage: Int = 1
	private val pages = ConcurrentHashMap<Int, Int>()

	fun getPage(offset: Int): Int {
		if (offset == 0) {
			return firstPage
		}
		pages[offset]?.let { return it }
		val pageSize = initialPageSize
		val intPage = offset / pageSize
		val tail = offset % pageSize
		return intPage + firstPage + if (tail == 0) 0 else 1
	}

	fun onListReceived(offset: Int, page: Int, count: Int) {
		pages[offset + count] = if (count > 0) page + 1 else page
	}
}
