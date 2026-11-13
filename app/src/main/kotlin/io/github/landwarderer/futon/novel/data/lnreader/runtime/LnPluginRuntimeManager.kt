package io.github.landwarderer.futon.novel.data.lnreader.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages singleton instances of [LnPluginRuntime] keyed by plugin ID.
 * Ensures runtimes are properly reused and explicitly closed on update or uninstall.
 */
@Singleton
class LnPluginRuntimeManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
	private val pluginStorage: LnPluginStorage,
) : Closeable {

	private val runtimes = ConcurrentHashMap<String, LnPluginRuntime>()

	fun getRuntime(plugin: LnPlugin): LnPluginRuntime {
		return runtimes.computeIfAbsent(plugin.id) {
			LnPluginRuntime(
				context = context,
				httpClient = httpClient,
				plugin = plugin,
				pluginStorage = pluginStorage,
			)
		}
	}

	fun closeRuntime(pluginId: String) {
		runtimes.remove(pluginId)?.close()
	}

	override fun close() {
		val iterator = runtimes.values.iterator()
		while (iterator.hasNext()) {
			val runtime = iterator.next()
			iterator.remove()
			runtime.close()
		}
	}
}

