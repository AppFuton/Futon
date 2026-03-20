package io.github.landwarderer.futon.history.data

import dagger.Reusable
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.entity.toManga
import io.github.landwarderer.futon.core.db.entity.toMangaTags
import io.github.landwarderer.futon.history.domain.model.MangaWithHistory
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.list.domain.ListSortOrder
import io.github.landwarderer.futon.local.data.index.LocalMangaIndex
import io.github.landwarderer.futon.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithManga, MangaWithHistory>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toManga(e: HistoryWithManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: HistoryWithManga, manga: Manga) = MangaWithHistory(
		manga = manga,
		history = e.history.toMangaHistory(),
	)
}
