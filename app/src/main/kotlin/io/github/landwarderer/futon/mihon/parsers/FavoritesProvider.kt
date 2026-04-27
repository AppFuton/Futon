package io.github.landwarderer.futon.mihon.parsers

import io.github.landwarderer.futon.mihon.parsers.model.Content

interface FavoritesProvider {

    suspend fun fetchFavorites(): List<Content>
}
