package io.github.landwarderer.futon.alternatives.ui.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class CoverHashTest {

	private val width = CoverHash.SAMPLE_WIDTH
	private val height = CoverHash.SAMPLE_HEIGHT

	@Test
	fun `identical images hash identically`() {
		val a = artwork(seed = 1)
		assertEquals(CoverHash.ofLuma(a), CoverHash.ofLuma(a.map { it.copyOf() }.toTypedArray()))
	}

	@Test
	fun `brightness shift is ignored`() {
		val a = artwork(seed = 1)
		val brighter = a.map { row -> IntArray(row.size) { (row[it] + 20).coerceAtMost(255) } }.toTypedArray()
		assertTrue(CoverHash.isSame(CoverHash.ofLuma(a), CoverHash.ofLuma(brighter)))
	}

	@Test
	fun `uniform padding is ignored`() {
		val a = artwork(seed = 2)
		val padded = pad(a, 8, 255)
		val distance = CoverHash.distance(CoverHash.ofLuma(a), CoverHash.ofLuma(padded))
		assertTrue("distance=$distance", distance <= CoverHash.MAX_DISTANCE)
	}

	@Test
	fun `unrelated artwork differs`() {
		val a = CoverHash.ofLuma(artwork(seed = 3))
		val b = CoverHash.ofLuma(artwork(seed = 4))
		assertFalse("distance=${CoverHash.distance(a, b)}", CoverHash.isSame(a, b))
	}

	@Test
	fun `uniform image does not crash`() {
		CoverHash.ofLuma(Array(height) { IntArray(width) { 128 } })
	}

	private fun artwork(seed: Int): Array<IntArray> {
		val rnd = Random(seed)
		val fx = rnd.nextDouble(0.05, 0.3)
		val fy = rnd.nextDouble(0.05, 0.3)
		val px = rnd.nextDouble(0.0, 6.0)
		val py = rnd.nextDouble(0.0, 6.0)
		val blobs = List(6) { Triple(rnd.nextInt(width), rnd.nextInt(height), rnd.nextInt(6, 20)) }
		return Array(height) { y ->
			IntArray(width) { x ->
				var v = 128 + 50 * sin(x * fx + px) + 50 * sin(y * fy + py)
				for ((bx, by, r) in blobs) {
					val dx = x - bx
					val dy = y - by
					if (dx * dx + dy * dy < r * r) v -= 70
				}
				v.toInt().coerceIn(0, 255)
			}
		}
	}

	/** Pads with a uniform border, then resamples back to the original size as a site would. */
	private fun pad(src: Array<IntArray>, border: Int, value: Int): Array<IntArray> {
		val w = src[0].size + border * 2
		val h = src.size + border * 2
		val big = Array(h) { y ->
			IntArray(w) { x ->
				val sy = y - border
				val sx = x - border
				if (sy in src.indices && sx in src[0].indices) src[sy][sx] else value
			}
		}
		return Array(height) { y -> IntArray(width) { x -> big[y * h / height][x * w / width] } }
	}
}
