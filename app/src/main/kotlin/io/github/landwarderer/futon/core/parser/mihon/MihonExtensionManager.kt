package io.github.landwarderer.futon.core.parser.mihon

import android.content.Context
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.parser.mihon.loader.MihonExtensionLoader
import io.github.landwarderer.futon.core.parser.mihon.model.MihonMangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Mihon (Tachiyomi) extensions, including discovery and life cycle.
 */
@Singleton
class MihonExtensionManager @Inject constructor(
	@ApplicationContext private val context: Context
) {
	private val loader = MihonExtensionLoader(context)
	private val sources = ArrayMap<Long, MihonMangaSource>()

	/**
	 * Scans for installed Mihon extensions and updates the internal cache.
	 * @return A list of found [MihonMangaSource]s.
	 */
	fun findExtensions(): List<MihonMangaSource> {
		val found = loader.loadExtensions()
		synchronized(sources) {
			sources.clear()
			for (source in found) {
				sources[source.id] = source
			}
		}
		return found
	}

	/**
	 * Retrieves a [MihonMangaSource] by its ID.
	 */
	fun getSource(id: Long): MihonMangaSource? {
		return synchronized(sources) {
			sources[id]
		}
	}
}
