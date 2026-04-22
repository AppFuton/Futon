package io.github.landwarderer.futon.mihon.model

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.toLocaleOrNull
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentType
import io.github.landwarderer.futon.mihon.parsers.util.splitTwoParts
import java.util.Locale

data object LocalMangaSource : ContentSource {
	override val name = "LOCAL"
	override val locale = ""
	override val contentType = ContentType.MANGA
}

val ContentSource.isLocal: Boolean
	get() = this == LocalMangaSource || this == LocalNovelSource || this == LocalVideoSource

data object LocalNovelSource : ContentSource {
	override val name = "LOCAL_NOVEL"
	override val locale = ""
	override val contentType = ContentType.NOVEL
}

data object LocalVideoSource : ContentSource {
	override val name = "LOCAL_VIDEO"
	override val locale = ""
	override val contentType = ContentType.VIDEO
}

data object UnknownContentSource : ContentSource {
	override val name = "UNKNOWN"
	override val locale = ""
	override val contentType = ContentType.OTHER
}

data object TestContentSource : ContentSource {
	override val name = "TEST"
	override val locale = ""
	override val contentType = ContentType.OTHER
}

data class ExternalContentSource(
	val packageName: String,
	val authority: String,
) : ContentSource {
	override val name: String
		get() = "content:$packageName/$authority"
	override val locale = ""
	override val contentType = ContentType.MANGA

	private var cachedName: String? = null

	fun isAvailable(context: Context): Boolean {
		return context.packageManager.resolveContentProvider(authority, 0)?.isEnabled == true
	}

	fun resolveName(context: Context): String {
		cachedName?.let {
			return it
		}
		val pm = context.packageManager
		val info = pm.resolveContentProvider(authority, 0)
		return info?.loadLabel(pm)?.toString()?.also {
			cachedName = it
		} ?: authority
	}
}

fun contentSource(name: String?): ContentSource {
	when (name ?: return UnknownContentSource) {
	UnknownContentSource.name -> return UnknownContentSource
	LocalMangaSource.name -> return LocalMangaSource
	LocalNovelSource.name -> return LocalNovelSource
	LocalVideoSource.name -> return LocalVideoSource
	TestContentSource.name -> return TestContentSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownContentSource
		return ExternalContentSource(packageName = parts.first, authority = parts.second)
	}
	// GlobalExtensionManager.mangaSources.value.find { it.name == name }?.let { return io.github.landwarderer.futon.core.parser.kotatsu.KotatsuParserSource(it) }
	// GlobalExtensionManager.contentSources.value.find { it.name == name }?.let { return it }

	// Fallbacks: If not loaded yet, return stable AnonymousContentSource
	// Keep the original name so it isn't lost if the source loads later
	return AnonymousContentSource(name)
}

fun Collection<String>.toContentSources() = map(::contentSource)

fun ContentSource.isNsfw(): Boolean = when (this) {
	is ContentSourceInfo -> mangaSource.isNsfw()
	is MihonMangaSource -> isNsfw
	else -> contentType in setOf(
		ContentType.HENTAI_MANGA,
		ContentType.HENTAI_NOVEL,
		ContentType.HENTAI_VIDEO,
	)
}

@get:StringRes
val ContentType.titleResId
	get() = when (this) {
		ContentType.MANGA -> R.string.content_type_manga
		ContentType.HENTAI_MANGA -> R.string.content_type_manga // Fallback
		ContentType.HENTAI_NOVEL -> R.string.content_type_novel // Fallback
		ContentType.HENTAI_VIDEO -> R.string.content_type_other // Fallback
		ContentType.COMICS -> R.string.content_type_comics
		ContentType.VIDEO -> R.string.content_type_other // Fallback
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

fun ContentType.getEnableSourceTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.enable_source_manga
	else -> R.string.enable_source_manga
}

fun ContentType.getUnsupportedSourceTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.unsupported_source
	else -> R.string.unsupported_source
}

fun ContentType.getDomainTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.domain_manga
	else -> R.string.domain_manga
}

fun ContentType.getSaveTitleResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.download_option_whole_manga_manga
	else -> R.string.download_option_whole_manga_manga
}

fun ContentType.getRecommendationTermResId(): Int = when (this) {
	ContentType.NOVEL, ContentType.HENTAI_NOVEL -> R.string.recommendation_manga
	else -> R.string.recommendation_manga
}

tailrec fun ContentSource.unwrap(): ContentSource = if (this is ContentSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun ContentSource.getLocale(): Locale? = unwrap().locale.takeIf { it.isNotEmpty() }?.toLocaleOrNull()

fun ContentSource.getContentType(): ContentType = unwrap().contentType

@RequiresApi(Build.VERSION_CODES.N)
fun ContentSource.getSummary(context: Context, contentType: ContentType? = null): String? = when (val source = unwrap()) {
	is io.github.landwarderer.futon.mihon.model.MihonMangaSource -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		val localeObj = source.locale.toLocaleOrNull() ?: Locale.getDefault()
		val locale = localeObj.getDisplayName(localeObj)
		val base = context.getString(R.string.source_summary_pattern, type, locale)
		appendOriginSuffix(context, base, source.getOriginLabel(context))
	}

	else -> {
		val resolvedContentType = contentType ?: getContentType()
		val type = context.getString(resolvedContentType.titleResId)
		val base = if (source.locale.isNotEmpty()) {
			val localeObj = source.locale.toLocaleOrNull() ?: Locale.getDefault()
			val locale = localeObj.getDisplayName(localeObj)
			context.getString(R.string.source_summary_pattern, type, locale)
		} else type
		appendOriginSuffix(context, base, source.getOriginLabel(context))
	}
}

@RequiresApi(Build.VERSION_CODES.N)
private fun appendOriginSuffix(context: Context, base: String, originLabel: String?): String {
	if (originLabel.isNullOrBlank()) {
		return base
	}
	val currentLanguage = context.resources.configuration.locales[0]?.language.orEmpty()
	val (open, close) = if (currentLanguage == "zh") "（" to "）" else "(" to ")"
	return "$base$open$originLabel$close"
}

fun ContentSource.getOriginLabel(context: Context): String? = when (this) {
	is ContentSourceInfo -> mangaSource.getOriginLabel(context)
	is ExternalContentSource -> context.getString(R.string.external_source)
	is io.github.landwarderer.futon.mihon.model.MihonMangaSource -> "Mihon"
	else -> null
}

fun ContentSource.getTitle(context: Context): String {
	val baseTitle = when (val source = unwrap()) {
		LocalMangaSource -> context.getString(R.string.local_storage)
		LocalNovelSource -> context.getString(R.string.domain_manga) + " " + context.getString(R.string.local_storage)
		LocalVideoSource -> context.getString(R.string.domain_manga) + " " + context.getString(R.string.local_storage)
		TestContentSource -> context.getString(R.string.test_parser)
		is ExternalContentSource -> source.resolveName(context)
		is io.github.landwarderer.futon.mihon.model.MihonMangaSource -> source.displayName
		else -> {
			source.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
		}
	}
	return if (this.isBroken) {
		"$baseTitle (Broken)"
	} else {
		baseTitle
	}
}

val ContentSource.isBroken: Boolean
	get() {
		val unwrapped = this.unwrap()
		return when (unwrapped) {
			is KotatsuParserSource -> unwrapped.isBroken
			else -> {
				// io.github.landwarderer.futon.core.extensions.GlobalExtensionManager.contentSources.value.find { it.originalSource == unwrapped || it.name == unwrapped.name }?.isBroken == true ||
				// io.github.landwarderer.futon.core.extensions.GlobalExtensionManager.mangaSources.value.find { it.originalSource == unwrapped || it.name == unwrapped.name }?.isBroken == true
				false
			}
		}
	}


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

private class AnonymousContentSource(override val name: String) : ContentSource {
	override val locale: String = ""
	override val contentType: ContentType get() = when {
		name.startsWith("ANIYOMI_") -> ContentType.VIDEO
		name.startsWith("JSON_TVBOX_") -> ContentType.VIDEO
		name.startsWith("JSON_LNREADER_") -> ContentType.NOVEL
		name.startsWith("JSON_LEGADO_M_") -> ContentType.MANGA
		name.startsWith("JSON_LEGADO_") -> ContentType.NOVEL
		name.startsWith("MIHON_") -> ContentType.MANGA
		name.startsWith("IREADER_") -> ContentType.NOVEL
		else -> ContentType.OTHER
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ContentSource) return false
		return name == other.name
	}

	override fun hashCode(): Int = name.hashCode()
	
	override fun toString(): String = "AnonymousContentSource(name=$name)"
}

/**
 * Maps IReader language/country codes to ISO 639-1 language codes.
 * IReader extensions use country codes (e.g., "cn") while App uses language codes (e.g., "zh").
 */
fun mapIReaderLangToLocale(lang: String): String? = when (lang.lowercase()) {
	"cn" -> "zh"
	"en" -> "en"
	"jp" -> "ja"
	"kr" -> "ko"
	"tw" -> "zh"
	"all" -> ""
	else -> lang  // Fallback: try using the code directly
}
