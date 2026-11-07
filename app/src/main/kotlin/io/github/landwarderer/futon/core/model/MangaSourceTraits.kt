package io.github.landwarderer.futon.core.model

import android.content.Context
import io.github.landwarderer.futon.core.util.ext.getDisplayName
import io.github.landwarderer.futon.core.util.ext.toLocaleOrNull
import io.github.landwarderer.futon.mihon.extensions.runtime.getExternalExtensionLanguageDisplayName
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.Locale
import io.github.landwarderer.futon.mihon.parsers.model.ContentType as MihonContentType

private val MULTI_LANGUAGE_CODES = setOf("all", "other", "multi")

/**
 * The language code as the source declares it (`en`, `pt`, `pt-BR`...), for both built-in parsers and
 * Mihon extensions, or null when the source is multilingual or does not say.
 */
val MangaSource.languageCode: String?
	get() = when (val source = unwrap()) {
		is MangaParserSource -> source.locale
		is MihonMangaSource -> source.language
		else -> null
	}?.trim()?.takeUnless { it.isEmpty() || it.lowercase(Locale.ROOT) in MULTI_LANGUAGE_CODES }

/** The bare language of [languageCode], lowercased (`pt-BR` -> `pt`), for comparing sources. */
val MangaSource.primaryLanguage: String?
	get() = languageCode?.let(::primaryLanguageOf)

fun primaryLanguageOf(code: String): String = code.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)

/** The content type of a built-in parser or of a Mihon extension, mapped onto the parsers library type. */
val MangaSource.contentTypeCompat: ContentType?
	get() = when (val source = unwrap()) {
		is MangaParserSource -> source.contentType
		is MihonMangaSource -> source.contentType.toContentType()
		else -> null
	}

fun MihonContentType.toContentType(): ContentType = when (this) {
	MihonContentType.MANGA -> ContentType.MANGA
	MihonContentType.MANHWA -> ContentType.MANHWA
	MihonContentType.MANHUA -> ContentType.MANHUA
	MihonContentType.HENTAI_MANGA, MihonContentType.HENTAI_NOVEL, MihonContentType.HENTAI_VIDEO -> ContentType.HENTAI
	MihonContentType.COMICS -> ContentType.COMICS
	MihonContentType.VIDEO -> ContentType.OTHER
	MihonContentType.NOVEL -> ContentType.NOVEL
	MihonContentType.ONE_SHOT -> ContentType.ONE_SHOT
	MihonContentType.DOUJINSHI -> ContentType.DOUJINSHI
	MihonContentType.IMAGE_SET -> ContentType.IMAGE_SET
	MihonContentType.ARTIST_CG -> ContentType.ARTIST_CG
	MihonContentType.GAME_CG -> ContentType.GAME_CG
	MihonContentType.OTHER -> ContentType.OTHER
}

/** A display name for a source language code, covering the codes only Mihon extensions use. */
fun getSourceLanguageDisplayName(context: Context, code: String): String =
	code.toLocaleOrNull()?.getDisplayName(context) ?: getExternalExtensionLanguageDisplayName(code)
