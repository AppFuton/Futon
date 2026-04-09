package io.github.landwarderer.futon.reader.domain

import androidx.annotation.WorkerThread
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.landwarderer.futon.core.util.ext.MimeType
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.local.data.LocalStorageCache
import io.github.landwarderer.futon.local.data.PageCache
import okio.Buffer
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@ActivityRetainedScoped
class ReaderOfflineCache @Inject constructor(
	@PageCache private val pagesCache: LocalStorageCache,
) {

	suspend fun putChapterPages(chapterId: Long, source: MangaSource, pages: List<MangaPage>) {
		val payload = json.encodeToString(
			pages.map {
				CachedPage(
					id = it.id,
					url = it.url,
					preview = it.preview,
				)
			},
		)
		putValue(chapterKey(source, chapterId), payload)
	}

	suspend fun getChapterPages(chapterId: Long, source: MangaSource): List<MangaPage>? {
		val payload = getValue(chapterKey(source, chapterId)) ?: return null
		return runCatchingCancellable {
			json.decodeFromString<List<CachedPage>>(payload).map {
				MangaPage(
					id = it.id,
					url = it.url,
					preview = it.preview,
					source = source,
				)
			}
		}.onFailure {
			it.printStackTraceDebug("ReaderOfflineCache::getChapterPages")
		}.getOrNull()
	}

	suspend fun putResolvedPageUrl(pageId: Long, source: MangaSource, pageUrl: String) {
		if (pageUrl.isBlank()) {
			return
		}
		putValue(pageUrlKey(source, pageId), pageUrl)
	}

	suspend fun getResolvedPageUrl(pageId: Long, source: MangaSource): String? {
		return getValue(pageUrlKey(source, pageId))?.takeIf { it.isNotBlank() }
	}

	private suspend fun putValue(key: String, value: String) {
		runCatchingCancellable {
			pagesCache.set(
				url = key,
				source = Buffer().writeUtf8(value),
				mimeType = MimeType("text/plain"),
			)
		}.onFailure {
			it.printStackTraceDebug("ReaderOfflineCache::putValue")
		}
	}

	@WorkerThread
	private suspend fun getValue(key: String): String? {
		val file = pagesCache[key] ?: return null
		return runCatchingCancellable {
			runInterruptible(Dispatchers.IO) {
				file.readText()
			}
		}.onFailure {
			it.printStackTraceDebug("ReaderOfflineCache::getValue")
		}.getOrNull()
	}

	private fun chapterKey(source: MangaSource, chapterId: Long) = "chapter_pages:${source.name}:$chapterId"

	private fun pageUrlKey(source: MangaSource, pageId: Long) = "page_url:${source.name}:$pageId"

	@Serializable
	private data class CachedPage(
		val id: Long,
		val url: String,
		val preview: String?,
	)

	private companion object {

		val json = Json {
			ignoreUnknownKeys = true
		}
	}
}
