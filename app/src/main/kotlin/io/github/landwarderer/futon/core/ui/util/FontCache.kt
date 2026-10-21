package io.github.landwarderer.futon.core.ui.util

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object FontCache {

	private val cache = ConcurrentHashMap<String, Typeface>()

	fun getTypeface(context: Context, assetPath: String): Typeface? {
		return cache.getOrPut(assetPath) {
			runCatching {
				Typeface.createFromAsset(context.assets, assetPath)
			}.getOrNull() ?: return null
		}
	}
}
