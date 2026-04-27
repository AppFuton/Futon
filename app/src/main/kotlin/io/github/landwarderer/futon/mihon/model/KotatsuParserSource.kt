package io.github.landwarderer.futon.mihon.model

import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

data class KotatsuParserSource(
    val mangaSource: MangaParserSource
) : ContentSource {
    override val name: String get() = mangaSource.name
    override val locale: String get() = mangaSource.locale
    override val contentType: ContentType get() = when (mangaSource.contentType) {
        org.koitharu.kotatsu.parsers.model.ContentType.MANGA -> ContentType.MANGA
        org.koitharu.kotatsu.parsers.model.ContentType.HENTAI -> ContentType.HENTAI_MANGA
        org.koitharu.kotatsu.parsers.model.ContentType.COMICS -> ContentType.COMICS
        org.koitharu.kotatsu.parsers.model.ContentType.OTHER -> ContentType.OTHER
        org.koitharu.kotatsu.parsers.model.ContentType.MANHWA -> ContentType.MANHWA
        org.koitharu.kotatsu.parsers.model.ContentType.MANHUA -> ContentType.MANHUA
        org.koitharu.kotatsu.parsers.model.ContentType.NOVEL -> ContentType.NOVEL
        org.koitharu.kotatsu.parsers.model.ContentType.ONE_SHOT -> ContentType.ONE_SHOT
        org.koitharu.kotatsu.parsers.model.ContentType.DOUJINSHI -> ContentType.DOUJINSHI
        org.koitharu.kotatsu.parsers.model.ContentType.IMAGE_SET -> ContentType.IMAGE_SET
        org.koitharu.kotatsu.parsers.model.ContentType.ARTIST_CG -> ContentType.ARTIST_CG
        org.koitharu.kotatsu.parsers.model.ContentType.GAME_CG -> ContentType.GAME_CG
    }
    val isBroken: Boolean get() = mangaSource.isBroken
}
