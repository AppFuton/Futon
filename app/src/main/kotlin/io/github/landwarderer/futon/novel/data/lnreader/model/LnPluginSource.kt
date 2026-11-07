package io.github.landwarderer.futon.novel.data.lnreader.model

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * MangaSource adapter for an LNReader plugin.
 * Unique source name format: "lnreader:{pluginId}"
 */
data class LnPluginSource(
	val plugin: LnPlugin,
) : MangaSource {

	override val name: String
		get() = "$PREFIX${plugin.id}"

	val title: String
		get() = plugin.name

	val locale: String
		get() = plugin.lang

	val contentType: ContentType
		get() = ContentType.NOVEL

	companion object {
		const val PREFIX = "lnreader:"

		fun isLnReaderSource(name: String): Boolean = name.startsWith(PREFIX)

		fun extractPluginId(name: String): String = name.removePrefix(PREFIX)
	}
}

