package io.github.landwarderer.futon.novel.data.lnreader.repository

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
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPluginSource
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages remote LNReader repositories and downloading/installing plugins.
 */
@Singleton
class LnPluginManager @Inject constructor(
	@BaseHttpClient private val httpClient: OkHttpClient,
	val storage: LnPluginStorage,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _installedPlugins = MutableStateFlow<List<LnPlugin>>(emptyList())
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

			val responseBody = httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching plugins: $repoUrl")
				response.body?.string().orEmpty()
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
			val script = httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("Failed to download plugin from ${plugin.url}")
				response.body?.string() ?: throw IOException("Empty response body")
			}
			storage.savePlugin(plugin, script)
			refreshInstalledPlugins()
			true
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			false
		}
	}

	suspend fun uninstallPlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
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
		return installedPlugins.value.map { LnPluginSource(it) }
	}

	fun getPluginSource(sourceName: String): LnPluginSource? {
		if (!LnPluginSource.isLnReaderSource(sourceName)) return null
		val pluginId = LnPluginSource.extractPluginId(sourceName)
		val plugin = installedPlugins.value.find { it.id == pluginId } ?: return null
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
