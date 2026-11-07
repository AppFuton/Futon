package io.github.landwarderer.futon.settings.override

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.model.unwrap
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.ui.model.MangaOverride
import io.github.landwarderer.futon.core.util.MimeTypes
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.isFileUri
import io.github.landwarderer.futon.core.util.ext.isHttpUrl
import io.github.landwarderer.futon.core.util.ext.openSource
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.core.util.ext.toMimeTypeOrNull
import io.github.landwarderer.futon.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.md5
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val DIR_COVERS = "covers"

@HiltViewModel
class OverrideConfigViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	@ApplicationContext private val context: Context,
	private val dataRepository: MangaDataRepository,
	@MangaHttpClient private val httpClient: OkHttpClient,
) : BaseViewModel() {

	private val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	val data = MutableStateFlow<Pair<Manga, MangaOverride>?>(null)
	val onSaved = MutableEventFlow<Unit>()

	/** Source a remote cover was picked from, so it is downloaded with that source's headers. */
	@Volatile
	private var remoteCoverSource: MangaSource? = null

	init {
		launchLoadingJob(Dispatchers.IO) {
			data.value = manga to (dataRepository.getOverride(manga.id) ?: emptyOverride())
		}
	}

	fun save(title: String?) {
		launchLoadingJob(Dispatchers.IO) {
			val override = checkNotNull(data.value).second.let {
				it.copy(
					title = title,
					coverUrl = it.coverUrl?.cachedFile(),
				)
			}
			dataRepository.setOverride(manga, override)
			onSaved.call(Unit)
		}
	}

	fun updateCover(coverUri: String?, source: MangaSource? = null) {
		val snapshot = data.value ?: return
		remoteCoverSource = source
		data.value = snapshot.first to snapshot.second.copy(
			coverUrl = coverUri,
		)
	}

	private suspend fun String.cachedFile(): String {
		val uri = toUriOrNull()
		if (uri == null || uri.isFileUri()) {
			return this
		}
		val cacheDir = context.getExternalFilesDir(DIR_COVERS) ?: return this
		// A cover chosen from another source is a remote url, which the ContentResolver cannot open.
		// Download it instead, so the override keeps working even if that site later blocks hotlinking
		// or moves the file.
		if (isHttpUrl()) {
			return downloadCover(cacheDir)
		}
		val cr = context.contentResolver
		val ext = cr.getType(uri)?.toMimeTypeOrNull()?.let {
			MimeTypes.getExtension(it)
		}
		val fileName = buildString {
			append(this@cachedFile.md5())
			if (!ext.isNullOrEmpty()) {
				append('.')
				append(ext)
			}
		}
		return withContext(Dispatchers.IO) {
			val dest = File(cacheDir, fileName)
			cr.openSource(uri).use { source ->
				dest.sink().buffer().use { sink ->
					sink.writeAll(source)
				}
			}
			dest
		}.toUri().toString()
	}

	private suspend fun String.downloadCover(cacheDir: File): String = withContext(Dispatchers.IO) {
		val url = this@downloadCover
		val request = Request.Builder()
			.url(url)
			.get()
			.apply {
				// CommonHeadersInterceptor adds the source's referer and user agent from this tag.
				remoteCoverSource?.unwrap()?.let { tag(MangaSource::class.java, it) }
			}
			.build()
		httpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				throw IOException("Cannot download cover: HTTP ${response.code}")
			}
			val ext = response.body.contentType()?.toString()?.substringBefore(';')
				?.toMimeTypeOrNull()
				?.let { MimeTypes.getExtension(it) }
				.ifNullOrEmpty {
					// Some CDNs answer with a generic content type; the url usually still names the format.
					url.substringBefore('?').substringAfterLast('.', "")
						.takeIf { it.length in 1..4 }
				}
			val dest = File(
				cacheDir,
				buildString {
					append(url.md5())
					if (!ext.isNullOrEmpty()) {
						append('.')
						append(ext)
					}
				},
			)
			dest.sink().buffer().use { sink ->
				sink.writeAll(response.body.source())
			}
			dest.toUri().toString()
		}
	}

	private fun emptyOverride() = MangaOverride(null, null, null)
}
