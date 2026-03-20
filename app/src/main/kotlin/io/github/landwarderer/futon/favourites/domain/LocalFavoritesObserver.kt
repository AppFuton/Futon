package io.github.landwarderer.futon.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.entity.toManga
import io.github.landwarderer.futon.core.db.entity.toMangaTags
import io.github.landwarderer.futon.favourites.data.FavouriteManga
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.list.domain.ListSortOrder
import io.github.landwarderer.futon.local.data.index.LocalMangaIndex
import io.github.landwarderer.futon.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteManga, Manga>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toManga(e: FavouriteManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: FavouriteManga, manga: Manga) = manga
}
