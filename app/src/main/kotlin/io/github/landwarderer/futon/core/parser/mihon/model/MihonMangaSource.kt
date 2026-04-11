package io.github.landwarderer.futon.core.parser.mihon.model

import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Represents a Mihon (Tachiyomi) extension source within the Futon app.
 *
 * @property id The unique identifier for the source, usually provided by the extension.
 * @property title The display name of the source.
 * @property packageName The Android package name of the extension APK.
 * @property className The fully qualified name of the source class in the extension.
 * @property factoryClassName Optional factory class name if the source is created via a SourceFactory.
 */
data class MihonMangaSource(
	val id: Long,
	val title: String,
	val packageName: String,
	val className: String,
	val factoryClassName: String? = null
) : MangaSource {

	override val name: String
		get() = "mihon:$id:$title:$packageName:$className" + (if (factoryClassName != null) ":$factoryClassName" else "")

	override fun toString(): String = name
}
