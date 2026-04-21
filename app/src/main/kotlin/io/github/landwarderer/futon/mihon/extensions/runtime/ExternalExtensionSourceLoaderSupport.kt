package io.github.landwarderer.futon.mihon.extensions.runtime

import android.util.Log
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

object ExternalExtensionSourceLoaderSupport {

	fun resolveSourceClassNames(
		pkgName: String,
		sourceClassNames: String,
	): List<String> {
		return sourceClassNames.split(";")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.map { className ->
				if (className.startsWith(".")) {
					pkgName + className
				} else {
					className
				}
			}
	}

	fun <SourceT> loadSources(
		pkgName: String,
		sourceClassNames: String,
		classLoader: ClassLoader,
		asSource: (Any) -> SourceT?,
		createSourcesFromFactory: (Any) -> List<SourceT>?,
		onUnknownInstance: (String) -> Unit = {},
	): List<SourceT> {
		val names = resolveSourceClassNames(pkgName, sourceClassNames)
		return names.flatMap { fullClassName ->
			try {
				val clazz = try {
					classLoader.loadClass(fullClassName)
				} catch (e: ClassNotFoundException) {
					Class.forName(fullClassName)
				}
				
				val instance = try {
					val constructor = clazz.getDeclaredConstructor()
					constructor.isAccessible = true
					constructor.newInstance()
				} catch (e: Exception) {
					try {
						val field = clazz.getField("INSTANCE")
						field.isAccessible = true
						field.get(null)
					} catch (e2: Exception) {
						throw Exception("Could not instantiate $fullClassName: no constructor or INSTANCE field")
					}
				}
				
				asSource(instance)?.let { source ->
					return@flatMap listOf(source)
				}
				
				// Fallback to shim interface check
				if (instance is Source) {
					@Suppress("UNCHECKED_CAST")
					return@flatMap listOf(instance as SourceT)
				}
				
				createSourcesFromFactory(instance)?.let { sources ->
					return@flatMap sources
				}
				
				// Fallback to shim factory check
				if (instance is SourceFactory) {
					@Suppress("UNCHECKED_CAST")
					return@flatMap instance.createSources() as List<SourceT>
				}

				onUnknownInstance(instance.javaClass.name)
				emptyList()
			} catch (e: Throwable) {
				Log.e("MihonExtensionLoader", "Error loading class $fullClassName", e)
				onUnknownInstance("Failed to load $fullClassName: ${e.message}")
				emptyList()
			}
		}
	}
}
