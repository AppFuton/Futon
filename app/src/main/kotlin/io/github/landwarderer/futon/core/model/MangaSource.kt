package io.github.landwarderer.futon.core.model

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.parser.external.ExternalMangaSource
import io.github.landwarderer.futon.core.util.ext.getDisplayName
import io.github.landwarderer.futon.core.util.ext.toLocale
import io.github.landwarderer.futon.core.util.ext.toLocaleOrNull
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentType as MihonContentType
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.splitTwoParts
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val MIHON_TITLES = ConcurrentHashMap<String, String>()

fun updateMihonTitle(name: String, title: String) {
	MIHON_TITLES[name] = title
}

data object LocalMangaSource : MangaSource {
	override val name = "LOCAL"
}

data object UnknownMangaSource : MangaSource {
	override val name = "UNKNOWN"
}

data object TestMangaSource : MangaSource {
	override val name = "TEST"
}

fun MangaSource(name: String?, title: String? = null): MangaSource {
	when (name ?: return UnknownMangaSource) {
		UnknownMangaSource.name -> return UnknownMangaSource
		LocalMangaSource.name -> return LocalMangaSource
		TestMangaSource.name -> return TestMangaSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownMangaSource
		return ExternalMangaSource(packageName = parts.first, authority = parts.second)
	}
	if (name.startsWith("mihon:") || name.startsWith("MIHON_")) {
		return AnonymousMangaSource(name, title)
	}
	MangaParserSource.entries.forEach {
		if (it.name == name) return it
	}
	return UnknownMangaSource
}

data class AnonymousMangaSource(
	override val name: String,
	val title: String? = null
) : MangaSource

fun Collection<String>.toMangaSources() = map { MangaSource(it) }

fun MangaSource.isNsfw(): Boolean = when (val source = unwrap()) {
	is MangaSourceInfo -> source.mangaSource.isNsfw()
	is MangaParserSource -> source.contentType == ContentType.HENTAI
	is MihonMangaSource -> source.isNsfw
	else -> false
}

@get:StringRes
val ContentType.titleResId
	get() = when (this) {
		ContentType.MANGA -> R.string.content_type_manga
		ContentType.HENTAI -> R.string.content_type_hentai
		ContentType.COMICS -> R.string.content_type_comics
		ContentType.OTHER -> R.string.content_type_other
		ContentType.MANHWA -> R.string.content_type_manhwa
		ContentType.MANHUA -> R.string.content_type_manhua
		ContentType.NOVEL -> R.string.content_type_novel
		ContentType.ONE_SHOT -> R.string.content_type_one_shot
		ContentType.DOUJINSHI -> R.string.content_type_doujinshi
		ContentType.IMAGE_SET -> R.string.content_type_image_set
		ContentType.ARTIST_CG -> R.string.content_type_artist_cg
		ContentType.GAME_CG -> R.string.content_type_game_cg
	}

tailrec fun MangaSource.unwrap(): MangaSource = if (this is MangaSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun MangaSource.getLocale(): Locale? = (unwrap() as? MangaParserSource)?.locale?.toLocaleOrNull()

fun MangaSource.getSummary(context: Context): String? = when (val source = unwrap()) {
	is MangaParserSource -> {
		val type = context.getString(source.contentType.titleResId)
		val locale = source.locale.toLocale().getDisplayName(context)
		context.getString(R.string.source_summary_pattern, type, locale)
	}

	is ExternalMangaSource -> context.getString(R.string.external_source)

	is MihonMangaSource -> {
		val contentType = when (source.contentType) {
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
		val type = context.getString(contentType.titleResId)
		val locale = source.language.toLocaleOrNull()?.getDisplayName(context) ?: source.language
		context.getString(R.string.source_summary_pattern, type, locale)
	}

	else -> null
}

fun MangaSource.getTitle(context: Context): String = when (val source = unwrap()) {
	is MangaParserSource -> source.title
	LocalMangaSource -> context.getString(R.string.local_storage)
	TestMangaSource -> context.getString(R.string.test_parser)
	is ExternalMangaSource -> source.resolveName(context)
	is MihonMangaSource -> source.displayName.also { updateMihonTitle(source.name, it) }
	is AnonymousMangaSource -> MIHON_TITLES[source.name] ?: source.title ?: context.getString(R.string.unknown)
	else -> context.getString(R.string.unknown)
}

val MangaSource.isBroken: Boolean
	get() = (this as? MangaParserSource)?.isBroken == true

fun SpannableStringBuilder.appendIcon(textView: TextView, @DrawableRes resId: Int): SpannableStringBuilder {
	val icon = ContextCompat.getDrawable(textView.context, resId) ?: return this
	icon.setTintList(textView.textColors)
	val size = textView.lineHeight
	icon.setBounds(0, 0, size, size)
	val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ImageSpan.ALIGN_CENTER
	} else {
		ImageSpan.ALIGN_BOTTOM
	}
	return inSpans(ImageSpan(icon, alignment)) { append(' ') }
}
