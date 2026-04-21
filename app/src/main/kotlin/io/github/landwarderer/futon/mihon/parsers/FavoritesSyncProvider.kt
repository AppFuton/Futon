package io.github.landwarderer.futon.mihon.parsers

import io.github.landwarderer.futon.mihon.parsers.model.Content

interface FavoritesSyncProvider {

    suspend fun addFavorite(manga: Content): Boolean

    suspend fun removeFavorite(manga: Content): Boolean
}
