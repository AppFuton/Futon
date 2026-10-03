package io.github.landwarderer.futon.novel.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.model.LocalNovelSource
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.epub.EpubParser
import io.github.landwarderer.futon.novel.data.pdf.PdfRendererHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelStorageManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val mangaRepository: MangaDataRepository,
) {

	val novelsDir: File
		get() {
			val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "novels")
			if (!dir.exists()) dir.mkdirs()
			return dir
		}

	suspend fun importNovel(uri: Uri): Manga? = withContext(Dispatchers.IO) {
		try {
			val fileName = queryDisplayName(uri) ?: "novel_${System.currentTimeMillis()}"
			val destFile = File(novelsDir, fileName)

			context.contentResolver.openInputStream(uri)?.use { input ->
				FileOutputStream(destFile).use { output ->
					input.copyTo(output)
				}
			} ?: return@withContext null

			parseFileToManga(destFile)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	suspend fun getAllNovels(): List<Manga> = withContext(Dispatchers.IO) {
		val files = novelsDir.listFiles() ?: return@withContext emptyList()
		files.mapNotNull { file ->
			if (file.extension.equals("epub", ignoreCase = true) || file.extension.equals("pdf", ignoreCase = true)) {
				parseFileToManga(file)
			} else null
		}
	}

	private suspend fun parseFileToManga(file: File): Manga? {
		return try {
			val isEpub = file.extension.equals("epub", ignoreCase = true)
			val isPdf = file.extension.equals("pdf", ignoreCase = true)
			if (!isEpub && !isPdf) return null

			val title = file.nameWithoutExtension
			val mangaId = file.absolutePath.longHashCode()

			val manga = if (isEpub) {
				val epubParser = EpubParser(file)
				val book = epubParser.parseBook()
				val chapters = book?.chapters?.map { ec ->
					MangaChapter(
						id = ec.id,
						title = ec.title,
						number = ec.index.toFloat(),
						volume = 0,
						url = ec.href,
						uploadDate = 0L,
						source = LocalNovelSource,
						scanlator = null,
						branch = null,
					)
				} ?: emptyList()

				Manga(
					id = mangaId,
					title = book?.title ?: title,
					altTitle = null,
					url = file.absolutePath,
					publicUrl = file.absolutePath,
					rating = -1f,
					isNsfw = false,
					coverUrl = "",
					tags = emptySet<MangaTag>(),
					state = null,
					author = book?.author,
					largeCoverUrl = null,
					description = book?.description,
					chapters = chapters,
					source = LocalNovelSource,
				)
			} else {
				val pdfHelper = PdfRendererHelper(file)
				val count = pdfHelper.pageCount
				val chapters = (0 until count).map { pageIdx ->
					MangaChapter(
						id = pageIdx.toLong(),
						title = "Page ${pageIdx + 1}",
						number = (pageIdx + 1).toFloat(),
						volume = 0,
						url = "pdf://page/$pageIdx",
						uploadDate = 0L,
						source = LocalNovelSource,
						scanlator = null,
						branch = null,
					)
				}
				pdfHelper.close()

				Manga(
					id = mangaId,
					title = title,
					altTitle = null,
					url = file.absolutePath,
					publicUrl = file.absolutePath,
					rating = -1f,
					isNsfw = false,
					coverUrl = "",
					tags = emptySet<MangaTag>(),
					state = null,
					author = null,
					largeCoverUrl = null,
					description = "PDF Document ($count pages)",
					chapters = chapters,
					source = LocalNovelSource,
				)
			}

			mangaRepository.storeManga(manga, replaceExisting = true)
			manga
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	private fun queryDisplayName(uri: Uri): String? {
		return try {
			context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (idx != -1) cursor.getString(idx) else null
				} else null
			}
		} catch (e: Exception) {
			null
		}
	}
}
