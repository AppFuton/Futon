package io.github.landwarderer.futon.core.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.util.component1
import coil3.util.component2
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlResizeFilter
import com.awxkee.jxlcoder.PreferredColorConfig
import com.awxkee.jxlcoder.ScaleMode
import com.davemorrissey.labs.subscaleview.decoder.ImageDecodeException
import kotlinx.coroutines.runInterruptible
import io.github.landwarderer.futon.core.util.MimeTypes

/**
 * JPEG XL for Coil, which Android cannot decode itself.
 *
 * Covers, thumbnails and page previews only ever need a small bitmap, so the image is decoded straight
 * to the requested size rather than in full and scaled down - a webtoon strip can be tens of
 * thousands of pixels tall.
 */
class JxlImageDecoder(
	private val source: ImageSource,
	private val options: Options,
) : Decoder {

	override suspend fun decode(): DecodeResult = runInterruptible {
		val bytes = source.source().readByteArray()
		val size = JxlCoder.getSize(bytes) ?: throw ImageDecodeException(
			uri = source.fileOrNull()?.toString(),
			format = FORMAT,
			message = "Cannot read the size of a JPEG XL image",
		)
		val (dstWidth, dstHeight) = DecodeUtils.computeDstSize(
			srcWidth = size.width,
			srcHeight = size.height,
			targetSize = options.size,
			scale = options.scale,
			maxSize = options.maxBitmapSize,
		)
		if (dstWidth < size.width || dstHeight < size.height) {
			DecodeResult(
				image = JxlCoder.decodeSampled(
					bytes,
					dstWidth,
					dstHeight,
					PreferredColorConfig.RGBA_8888,
					ScaleMode.FIT,
					JxlResizeFilter.BILINEAR,
				).asImage(),
				isSampled = true,
			)
		} else {
			DecodeResult(
				image = JxlCoder.decode(bytes, PreferredColorConfig.RGBA_8888, ScaleMode.FIT).asImage(),
				isSampled = false,
			)
		}
	}

	class Factory : Decoder.Factory {

		override fun create(
			result: SourceFetchResult,
			options: Options,
			imageLoader: ImageLoader
		): Decoder? = if (isApplicable(result) && JxlSupport.isAvailable) {
			JxlImageDecoder(result.source, options)
		} else {
			null
		}

		override fun equals(other: Any?) = other is Factory

		override fun hashCode() = javaClass.hashCode()

		// By content as well as by type: page caches have no extension, and sites serve JPEG XL
		// under the URL of the JPEG it replaced
		private fun isApplicable(result: SourceFetchResult): Boolean {
			return result.mimeType == MimeTypes.JXL.toString() || BitmapDecoderCompat.isJxl(result.source.source())
		}
	}

	private companion object {

		const val FORMAT = "jxl"
	}
}
