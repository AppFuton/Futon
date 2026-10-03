package io.github.landwarderer.futon.novel.data.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import java.io.File

class PdfRendererHelper(private val file: File) : AutoCloseable {

	private var pfd: ParcelFileDescriptor? = null
	private var renderer: PdfRenderer? = null

	val pageCount: Int
		get() = renderer?.pageCount ?: 0

	val isValid: Boolean
		get() = renderer != null && pageCount > 0

	init {
		try {
			val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
			pfd = descriptor
			renderer = PdfRenderer(descriptor)
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
		val r = renderer ?: return null
		if (pageIndex < 0 || pageIndex >= r.pageCount) return null
		return try {
			val page = r.openPage(pageIndex)
			val scale = targetWidth.toFloat() / page.width.toFloat()
			val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
			val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
			page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
			page.close()
			bitmap
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	fun renderThumbnail(pageIndex: Int = 0, thumbWidth: Int = 300): Bitmap? {
		return renderPage(pageIndex, thumbWidth)
	}

	override fun close() {
		try {
			renderer?.close()
			renderer = null
			pfd?.close()
			pfd = null
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}
}
