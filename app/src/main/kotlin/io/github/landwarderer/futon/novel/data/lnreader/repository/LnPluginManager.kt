package io.github.landwarderer.futon.novel.data.lnreader.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPluginSource
import io.github.landwarderer.futon.novel.data.lnreader.runtime.LnPluginRuntimeManager
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages remote LNReader repositories and downloading/installing plugins.
 */
@Singleton
class LnPluginManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient private val httpClient: OkHttpClient,
	val storage: LnPluginStorage,
	private val runtimeManager: LnPluginRuntimeManager,
	private val database: MangaDatabase,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _installedPlugins = MutableStateFlow<List<LnPlugin>>(
		runCatching { storage.getInstalledPluginsSync() }.getOrDefault(emptyList()),
	)
	val installedPlugins: StateFlow<List<LnPlugin>> = _installedPlugins.asStateFlow()

	init {
		scope.launch(Dispatchers.IO) {
			refreshInstalledPlugins()
		}
	}

	companion object {
		/** Default official LNReader community plugins repository manifest */
		const val DEFAULT_REPO_URL =
			"https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"
	}

	suspend fun refreshInstalledPlugins(): List<LnPlugin> = withContext(Dispatchers.IO) {
		val list = storage.getInstalledPlugins()
		_installedPlugins.value = list
		list
	}

	/**
	 * Fetches the list of all available plugins from remote repositories.
	 * Compares against locally installed plugins to mark `isInstalled` and `hasUpdate`.
	 */
	suspend fun fetchAvailablePlugins(repoUrl: String = DEFAULT_REPO_URL): List<LnPlugin> =
		withContext(Dispatchers.IO) {
			val request = Request.Builder().url(repoUrl).build()
			val installedMap = storage.getInstalledPlugins().associateBy { it.id }

			val responseBody = httpClient.newCall(request).awaitSuccess().use { response ->
				response.body.string()
			}

			val jsonArray = JSONArray(responseBody)
			val result = ArrayList<LnPlugin>(jsonArray.length())
			for (i in 0 until jsonArray.length()) {
				val itemObj = jsonArray.getJSONObject(i)
				val pluginId = itemObj.getString("id")
				val installed = installedMap[pluginId]
				val remoteVersion = itemObj.optString("version", "1.0.0")
				val hasUpdate = installed != null && isNewerVersion(remoteVersion, installed.version)

				result.add(
					LnPlugin.fromJson(
						itemObj,
						isInstalled = installed != null,
						hasUpdate = hasUpdate,
					),
				)
			}
			result
		}

	/**
	 * Downloads and saves a plugin's `index.js` script to local storage.
	 */
	suspend fun installPlugin(plugin: LnPlugin): Boolean = withContext(Dispatchers.IO) {
		try {
			val request = Request.Builder().url(plugin.url).build()
			val script = httpClient.newCall(request).awaitSuccess().use { response ->
				response.body.string()
			}
			val customJs = plugin.customJsUrl?.let { downloadAsset(it) }
			val customCss = plugin.customCssUrl?.let { downloadAsset(it) }
			storage.savePlugin(plugin, script, customJs, customCss)
			runtimeManager.closeRuntime(plugin.id)
			refreshInstalledPlugins()
			true
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			false
		}
	}

	private suspend fun downloadAsset(url: String): String {
		val request = Request.Builder().url(url).build()
		return httpClient.newCall(request).awaitSuccess().use { response ->
			response.body.string()
		}
	}

	suspend fun uninstallPlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
		runtimeManager.closeRuntime(pluginId)
		// Clean up plugin SharedPreferences
		context.getSharedPreferences("ln_plugin_$pluginId", Context.MODE_PRIVATE).edit().clear().apply()
		// Remove source from database
		database.getSourcesDao().delete("${LnPluginSource.PREFIX}$pluginId")

		val success = storage.deletePlugin(pluginId)
		if (success) {
			refreshInstalledPlugins()
		}
		success
	}

	suspend fun getInstalledPlugins(): List<LnPlugin> {
		return storage.getInstalledPlugins()
	}

	fun getInstalledSources(): List<LnPluginSource> {
		val list = installedPlugins.value.ifEmpty {
			storage.getInstalledPluginsSync()
		}
		return list.map { LnPluginSource(it) }
	}

	fun getPluginSource(sourceName: String): LnPluginSource? {
		if (!LnPluginSource.isLnReaderSource(sourceName)) return null
		val pluginId = LnPluginSource.extractPluginId(sourceName)
		val plugin = installedPlugins.value.find { it.id == pluginId }
			?: storage.getPlugin(pluginId)
			?: return null
		return LnPluginSource(plugin)
	}

	/**
	 * Simple semver comparator: returns true if v1 > v2.
	 */
	fun isNewerVersion(v1: String, v2: String): Boolean {
		val p1 = v1.split('.').mapNotNull { it.toIntOrNull() }
		val p2 = v2.split('.').mapNotNull { it.toIntOrNull() }
		val maxLen = maxOf(p1.size, p2.size)
		for (i in 0 until maxLen) {
			val num1 = p1.getOrElse(i) { 0 }
			val num2 = p2.getOrElse(i) { 0 }
			if (num1 > num2) return true
			if (num1 < num2) return false
		}
		return false
	}
}
