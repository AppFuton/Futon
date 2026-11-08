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

	override suspend fun getPageUrl(page: MangaPage): String = page.url
	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / 20) + 1
		val query = filter?.query?.trim().orEmpty()

		val items = if (query.isNotEmpty()) {
			runtime.searchNovels(query, page)
		} else {
			runtime.getPopularNovels(page)
		}

		return items.map { item ->
			val fullUrl = resolveUrl(item.path)
			@Suppress("DEPRECATION")
			Manga(
				id = "${plugin.id}_${item.path}".longHashCode(),
				title = item.name,
				altTitle = null,
				url = item.path,
				publicUrl = fullUrl,
				rating = 0f,
				isNsfw = false,
				coverUrl = item.cover.orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
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
			val chapUrl = resolveUrl(chapterItem.path)
			MangaChapter(
				id = "${plugin.id}_${chapterItem.path}".longHashCode(),
				title = chapterItem.name,
				number = chapterItem.chapterNumber ?: (index + 1).toFloat(),
				volume = 0,
				url = chapterItem.path,
				uploadDate = 0L,
				source = source,
				scanlator = null,
				branch = null,
			)
		}

		val tags = novel.genres?.split(",")?.map { g ->
			val trimmed = g.trim()
			MangaTag(title = trimmed, key = trimmed, source = source)
		}?.toSet().orEmpty()

		return manga.copy(
			description = novel.summary ?: manga.description,
			coverUrl = novel.cover ?: manga.coverUrl,
			authors = novel.author?.let { setOf(it) } ?: manga.authors,
			tags = if (tags.isNotEmpty()) tags else manga.tags,
			chapters = chapters,
		)
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val html = runtime.parseChapter(chapter.url)
		return listOf(
			MangaPage(
				id = chapter.id,
				url = html,
				preview = resolveUrl(chapter.url),
				source = source,
			)
		)
	}

	private fun resolveUrl(path: String): String {
		if (path.startsWith("http://") || path.startsWith("https://")) return path
		val siteBase = plugin.site.trimEnd('/')
		val cleanPath = path.trimStart('/')
		return "$siteBase/$cleanPath"
	}
}

