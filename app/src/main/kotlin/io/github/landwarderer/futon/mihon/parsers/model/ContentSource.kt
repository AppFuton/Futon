package io.github.landwarderer.futon.mihon.parsers.model

import org.koitharu.kotatsu.parsers.model.MangaSource

interface ContentSource : MangaSource {

    override val name: String
    val locale: String
    val contentType: ContentType
}
