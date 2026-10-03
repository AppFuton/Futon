package io.github.landwarderer.futon.novel.data

import io.github.landwarderer.futon.core.model.LocalNovelSource
import io.github.landwarderer.futon.core.parser.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNovelRepository @Inject constructor(
	private val novelStorageManager: NovelStorageManager,
) : MangaRepository {

	override val source: MangaSource = LocalNovelSource

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	override var defaultSortOrder: SortOrder = SortOrder.NEWEST

	override val filterCapabilities = MangaListFilterCapabilities()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> = withContext(Dispatchers.IO) {
		val all = novelStorageManager.getAllNovels()
		val query = filter?.query
		val filtered = if (!query.isNullOrBlank()) {
			all.filter { it.title.contains(query, ignoreCase = true) }
		} else {
			all
		}
		filtered.drop(offset)
	}

	override suspend fun getDetails(manga: Manga): Manga = manga

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return listOf(
			MangaPage(
				id = chapter.id,
				url = chapter.url,
				preview = null,
				source = LocalNovelSource,
			)
		)
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getRelated(seed: Manga): List<Manga> = emptyList()
}
