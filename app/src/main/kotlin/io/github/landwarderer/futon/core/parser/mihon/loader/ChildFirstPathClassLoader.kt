package io.github.landwarderer.futon.core.parser.mihon.loader

import dalvik.system.PathClassLoader

/**
 * A custom [ClassLoader] that prioritizes loading classes from the extension APK before
 * delegating to the parent ClassLoader. This is used to prevent dependency clashes
 * between the host app and the Mihon plugin.
 *
 * Specific prefixes (like kotlin.*, android.*, etc.) are always delegated to the parent
 * to ensure compatibility with shared system and app APIs.
 */
class ChildFirstPathClassLoader(
	dexPath: String,
	librarySearchPath: String?,
	parent: ClassLoader
) : PathClassLoader(dexPath, librarySearchPath, parent) {

	private val parentClassLoader = parent

	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		// Always delegate these to parent
		if (name.startsWith("java.") ||
			name.startsWith("javax.") ||
			name.startsWith("android.") ||
			name.startsWith("androidx.") ||
			name.startsWith("kotlin.") ||
			name.startsWith("kotlinx.serialization.") ||
			name.startsWith("okhttp3.") ||
			name.startsWith("okio.") ||
			(name.startsWith("eu.kanade.tachiyomi.") && !name.startsWith("eu.kanade.tachiyomi.extension.")) ||
			name.startsWith("org.koitharu.kotatsu.parsers.") // Internal Futon/Kotatsu parser API
		) {
			return parentClassLoader.loadClass(name)
		}

		// Try loading from child first
		return try {
			findClass(name)
		} catch (e: ClassNotFoundException) {
			parentClassLoader.loadClass(name)
		}
	}
}
