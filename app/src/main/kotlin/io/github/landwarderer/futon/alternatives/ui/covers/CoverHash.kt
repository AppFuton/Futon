package io.github.landwarderer.futon.alternatives.ui.covers

import android.graphics.Bitmap
import androidx.core.graphics.get
import kotlin.math.cos

/**
 * Perceptual hash of a cover, for spotting the same artwork republished by another source.
 *
 * Sources re-encode, pad and re-scale the same image, so url or byte equality catches almost none of
 * the duplicates people actually see. Three steps make the comparison survive that:
 *
 * 1. trim near-uniform edges, which removes letterboxing and padding;
 * 2. centre-crop to a fixed aspect, so covers stored at different proportions still line up;
 * 3. hash the low-frequency DCT coefficients, which describe overall structure and ignore sharpening,
 *    compression and brightness.
 *
 * Known limit: a genuinely tighter crop of the same art still scores far apart.
 */
object CoverHash {

	/** Source bitmap size to hash. Big enough that trimming edges leaves real detail behind. */
	const val SAMPLE_WIDTH = 64
	const val SAMPLE_HEIGHT = 96

	/** Differing bits (out of 63) below which two covers are treated as the same image. */
	const val MAX_DISTANCE = 12

	private const val DCT_SIZE = 32
	private const val HASH_SIZE = 8
	private const val UNIFORM_TOLERANCE = 12
	private const val TARGET_ASPECT = 2f / 3f

	fun of(bitmap: Bitmap): Long = ofLuma(bitmap.toLuma())

	/**
	 * Hashes a luma matrix (rows of 0..255 values). Pure, so it can be unit tested without Android.
	 */
	fun ofLuma(luma: Array<IntArray>): Long {
		require(luma.isNotEmpty() && luma[0].isNotEmpty()) { "Empty image" }
		val trimmed = luma.autoCrop().centerToAspect()
		val matrix = trimmed.scaleTo(DCT_SIZE)
		val dct = matrix.dct2d()
		// Low-frequency block only, minus the DC term: DC is average brightness, which we want ignored.
		val values = DoubleArray(HASH_SIZE * HASH_SIZE - 1)
		var i = 0
		for (y in 0 until HASH_SIZE) {
			for (x in 0 until HASH_SIZE) {
				if (x != 0 || y != 0) {
					values[i++] = dct[y][x]
				}
			}
		}
		val median = values.sortedArray()[values.size / 2]
		var hash = 0L
		values.forEachIndexed { index, value ->
			if (value > median) {
				hash = hash or (1L shl index)
			}
		}
		return hash
	}

	fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

	fun isSame(a: Long, b: Long): Boolean = distance(a, b) <= MAX_DISTANCE

	private fun Bitmap.toLuma(): Array<IntArray> {
		// getPixel throws on a hardware bitmap, and a fixed-size copy is needed anyway.
		val software = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) ?: this else this
		val scaled = Bitmap.createScaledBitmap(software, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
		val out = Array(SAMPLE_HEIGHT) { IntArray(SAMPLE_WIDTH) }
		for (y in 0 until SAMPLE_HEIGHT) {
			for (x in 0 until SAMPLE_WIDTH) {
				val pixel = scaled[x, y]
				val r = (pixel shr 16) and 0xFF
				val g = (pixel shr 8) and 0xFF
				val b = pixel and 0xFF
				// Rec. 601 luma in integer maths; float precision is meaningless at this size.
				out[y][x] = (r * 299 + g * 587 + b * 114) / 1000
			}
		}
		if (scaled !== software) scaled.recycle()
		if (software !== this) software.recycle()
		return out
	}

	/** Drops near-uniform edge rows and columns, so padding and letterboxing stop counting as content. */
	private fun Array<IntArray>.autoCrop(): Array<IntArray> {
		var top = 0
		var bottom = size - 1
		while (top < bottom && isRowUniform(top)) top++
		while (bottom > top && isRowUniform(bottom)) bottom--
		var left = 0
		var right = this[0].size - 1
		while (left < right && isColumnUniform(left, top, bottom)) left++
		while (right > left && isColumnUniform(right, top, bottom)) right--
		return Array(bottom - top + 1) { y -> this[top + y].copyOfRange(left, right + 1) }
	}

	private fun Array<IntArray>.centerToAspect(): Array<IntArray> {
		val height = size
		val width = this[0].size
		val wantWidth = minOf(width, (height * TARGET_ASPECT).toInt()).coerceAtLeast(1)
		val wantHeight = minOf(height, (wantWidth / TARGET_ASPECT).toInt()).coerceAtLeast(1)
		val x0 = (width - wantWidth) / 2
		val y0 = (height - wantHeight) / 2
		return Array(wantHeight) { y -> this[y0 + y].copyOfRange(x0, x0 + wantWidth) }
	}

	private fun Array<IntArray>.scaleTo(size: Int): Array<DoubleArray> {
		val height = this.size
		val width = this[0].size
		return Array(size) { y ->
			DoubleArray(size) { x ->
				this[y * height / size][x * width / size].toDouble()
			}
		}
	}

	private fun Array<DoubleArray>.dct2d(): Array<DoubleArray> {
		val n = size
		val rows = Array(n) { y -> this[y].dct1d() }
		val out = Array(n) { DoubleArray(n) }
		val column = DoubleArray(n)
		for (x in 0 until n) {
			for (y in 0 until n) column[y] = rows[y][x]
			val transformed = column.dct1d()
			for (y in 0 until n) out[y][x] = transformed[y]
		}
		return out
	}

	private fun DoubleArray.dct1d(): DoubleArray {
		val n = size
		return DoubleArray(n) { k ->
			var sum = 0.0
			for (i in 0 until n) {
				sum += this[i] * cos(Math.PI * (i + 0.5) * k / n)
			}
			sum
		}
	}

	private fun Array<IntArray>.isRowUniform(y: Int): Boolean {
		val row = this[y]
		return row.max() - row.min() <= UNIFORM_TOLERANCE
	}

	private fun Array<IntArray>.isColumnUniform(x: Int, top: Int, bottom: Int): Boolean {
		var min = Int.MAX_VALUE
		var max = Int.MIN_VALUE
		for (y in top..bottom) {
			val v = this[y][x]
			if (v < min) min = v
			if (v > max) max = v
		}
		return max - min <= UNIFORM_TOLERANCE
	}
}
