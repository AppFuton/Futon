package io.github.landwarderer.futon.history.domain

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import io.github.landwarderer.futon.core.util.ext.mangaExtra
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.toBitmapOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_PARALLELISM = 3
private const val CACHE_SIZE = 512
private const val COVER_ASPECT = 13f / 18f

/** Measures where a cover has visual detail, so a spine can show the most interesting strip of it. */
@Singleton
class SpineCropAnalyzer @Inject constructor(
	@param:ApplicationContext private val context: Context,
	private val coil: ImageLoader,
) {

	private val cache = LruCache<Long, FloatArray>(CACHE_SIZE)
	private val semaphore = Semaphore(MAX_PARALLELISM)

	fun peekEnergy(manga: Manga): FloatArray? = cache[manga.id]

	suspend fun getEnergy(manga: Manga): FloatArray? {
		cache[manga.id]?.let { return it }
		return semaphore.withPermit {
			runCatchingCancellable {
				val request = ImageRequest.Builder(context)
					.data(manga.coverUrl)
					.mangaExtra(manga)
					.size(Size(SPINE_COLUMNS * 2, SPINE_ROWS * 2))
					.scale(Scale.FILL)
					.precision(Precision.INEXACT)
					.allowHardware(false)
					.build()
				coil.execute(request).toBitmapOrNull()?.let { bitmap ->
					withContext(Dispatchers.Default) { analyze(bitmap) }
				}
			}.onFailure {
				it.printStackTraceDebug("SpineCropAnalyzer")
			}.getOrNull()?.also { cache.put(manga.id, it) }
		}
	}

	private fun analyze(bitmap: Bitmap): FloatArray {
		val isWide = bitmap.width.toFloat() / bitmap.height > COVER_ASPECT
		val cropWidth = if (isWide) (bitmap.height * COVER_ASPECT).toInt().coerceAtLeast(1) else bitmap.width
		val cropHeight = if (isWide) bitmap.height else (bitmap.width / COVER_ASPECT).toInt().coerceAtLeast(1)
		val cropped = Bitmap.createBitmap(
			bitmap,
			(bitmap.width - cropWidth) / 2,
			(bitmap.height - cropHeight) / 2,
			cropWidth,
			cropHeight,
		)
		val scaled = Bitmap.createScaledBitmap(cropped, SPINE_COLUMNS, SPINE_ROWS, true)
		val pixels = IntArray(SPINE_COLUMNS * SPINE_ROWS)
		scaled.getPixels(pixels, 0, SPINE_COLUMNS, 0, 0, SPINE_COLUMNS, SPINE_ROWS)
		val luma = FloatArray(pixels.size) { i ->
			val color = pixels[i]
			0.2126f * (color shr 16 and 0xFF) + 0.7152f * (color shr 8 and 0xFF) + 0.0722f * (color and 0xFF)
		}
		return spineColumnEnergy(luma, SPINE_COLUMNS, SPINE_ROWS)
	}
}
