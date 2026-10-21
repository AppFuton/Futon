package io.github.landwarderer.futon.novel.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import java.io.File

class PdfRendererHelper : AutoCloseable {

	private var pfd: ParcelFileDescriptor? = null
	private var renderer: PdfRenderer? = null
	private val lock = Any()

	private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
	private val cacheSize = (maxMemory / 8).coerceAtLeast(1024) // 12.5% of max heap
	private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
		override fun sizeOf(key: Int, value: Bitmap): Int {
			return try {
				(value.byteCount / 1024).coerceAtLeast(1)
			} catch (e: Throwable) {
				1
			}
		}
	}

	val pageCount: Int
		get() = synchronized(lock) { renderer?.pageCount ?: 0 }

	val isValid: Boolean
		get() = synchronized(lock) { renderer != null && pageCount > 0 }

	constructor(file: File) {
		try {
			val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
			pfd = descriptor
			renderer = PdfRenderer(descriptor)
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	constructor(context: Context, uri: Uri) {
		try {
			val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
			pfd = descriptor
			if (descriptor != null) {
				renderer = PdfRenderer(descriptor)
			}
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
		val cached = bitmapCache.get(pageIndex)
		if (cached != null && cached.width >= targetWidth) {
			return cached
		}
		return synchronized(lock) {
			val secondCheck = bitmapCache.get(pageIndex)
			if (secondCheck != null && secondCheck.width >= targetWidth) {
				return@synchronized secondCheck
			}
			val r = renderer ?: return@synchronized null
			if (pageIndex < 0 || pageIndex >= r.pageCount) return@synchronized null
			try {
				val page = r.openPage(pageIndex)
				val scale = targetWidth.toFloat() / page.width.toFloat()
				val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
				val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
				page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
				page.close()
				bitmapCache.put(pageIndex, bitmap)
				bitmap
			} catch (e: Exception) {
				e.printStackTraceDebug()
				null
			}
		}
	}

	fun renderThumbnail(pageIndex: Int = 0, thumbWidth: Int = 300): Bitmap? {
		return renderPage(pageIndex, thumbWidth)
	}

	override fun close() {
		synchronized(lock) {
			try {
				bitmapCache.evictAll()
				renderer?.close()
				renderer = null
				pfd?.close()
				pfd = null
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}
}
