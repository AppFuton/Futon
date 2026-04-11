package io.github.landwarderer.futon.core.parser.mihon

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import java.util.Locale

/**
 * Utility functions to convert data models between Mihon (Tachiyomi) and Futon (Kotatsu).
 * Since Mihon uses its own internal models (SManga, SChapter, Page), we need to map
 * them to the models used by the Futon parser layer using reflection.
 */
object MihonDataConverters {

	fun toFutonManga(mihonManga: Any, source: MangaSource): Manga {
		val url = getStringField(mihonManga, "url") ?: ""
		val title = getStringField(mihonManga, "title") ?: ""
		val thumbnail = getStringField(mihonManga, "thumbnail_url")
		val author = getStringField(mihonManga, "author")
		val artist = getStringField(mihonManga, "artist")
		val genre = getStringField(mihonManga, "genre")
		val status = getIntField(mihonManga, "status")

		return Manga(
			id = (source.name + url).hashCode().toLong(),
			title = title,
			altTitles = emptySet(),
			url = url,
			publicUrl = url,
			rating = 0f,
			contentRating = null,
			coverUrl = thumbnail,
			tags = genre?.split(",")?.map { it.trim() }
				?.filter { it.isNotEmpty() }
				?.map { MangaTag(it, it, source) }
				?.toSet() ?: emptySet(),
			state = mapStatus(status),
			authors = listOfNotNull(author, artist).toSet(),
			source = source
		)
	}

	fun toFutonChapter(mihonChapter: Any, source: MangaSource): MangaChapter {
		val url = getStringField(mihonChapter, "url") ?: ""
		val name = getStringField(mihonChapter, "name") ?: ""
		val dateUpload = getLongField(mihonChapter, "date_upload") ?: 0L
		val chapterNumber = getFloatField(mihonChapter, "chapter_number") ?: -1f
		val scanlator = getStringField(mihonChapter, "scanlator")

		return MangaChapter(
			id = (source.name + url).hashCode().toLong(),
			title = name,
			url = url,
			number = chapterNumber,
			volume = 0,
			scanlator = scanlator,
			uploadDate = dateUpload,
			branch = null,
			source = source
		)
	}

	fun toFutonPage(mihonPage: Any, source: MangaSource): MangaPage {
		val index = getIntField(mihonPage, "index") ?: 0
		val url = getStringField(mihonPage, "url") ?: ""
		val imageUrl = getStringField(mihonPage, "imageUrl")

		return MangaPage(
			id = index.toLong(),
			url = imageUrl ?: url,
			preview = null,
			source = source
		)
	}

	private fun mapStatus(status: Int?): MangaState? = when (status) {
		1 -> MangaState.ONGOING // SManga.ONGOING
		2 -> MangaState.FINISHED // SManga.COMPLETED
		3 -> MangaState.PAUSED // SManga.LICENSED (closest match)
		else -> null
	}

	private fun getStringField(obj: Any, name: String): String? {
		return try {
			val field = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase(Locale.ROOT) }}")
			field.invoke(obj) as? String
		} catch (e: Exception) {
			try {
				val field = obj.javaClass.getField(name)
				field.get(obj) as? String
			} catch (e2: Exception) {
				null
			}
		}
	}

	private fun getIntField(obj: Any, name: String): Int? {
		return try {
			val field = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase(Locale.ROOT) }}")
			field.invoke(obj) as? Int
		} catch (e: Exception) {
			try {
				val field = obj.javaClass.getField(name)
				field.get(obj) as? Int
			} catch (e2: Exception) {
				null
			}
		}
	}

	private fun getLongField(obj: Any, name: String): Long? {
		return try {
			val field = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase(Locale.ROOT) }}")
			field.invoke(obj) as? Long
		} catch (e: Exception) {
			try {
				val field = obj.javaClass.getField(name)
				field.get(obj) as? Long
			} catch (e2: Exception) {
				null
			}
		}
	}

	private fun getFloatField(obj: Any, name: String): Float? {
		return try {
			val field = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase(Locale.ROOT) }}")
			field.invoke(obj) as? Float
		} catch (e: Exception) {
			try {
				val field = obj.javaClass.getField(name)
				field.get(obj) as? Float
			} catch (e2: Exception) {
				null
			}
		}
	}
}
