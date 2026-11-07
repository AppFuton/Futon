package io.github.landwarderer.futon.novel.data.lnreader.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages installed LNReader plugin files on disk.
 *
 * Directory structure:
 * `<filesDir>/lnreader_plugins/`
 *   ├── `<pluginId>/`
 *   │     ├── index.js     (compiled plugin bundle)
 *   │     └── meta.json    (metadata for installed version, name, etc.)
 */
@Singleton
class LnPluginStorage @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val baseDir: File by lazy {
		File(context.filesDir, "lnreader_plugins").apply { mkdirs() }
	}

	fun getPluginDir(pluginId: String): File = File(baseDir, pluginId)

	fun getPluginScript(pluginId: String): File = File(getPluginDir(pluginId), "index.js")

	private fun getPluginMeta(pluginId: String): File = File(getPluginDir(pluginId), "meta.json")

	fun isPluginInstalled(pluginId: String): Boolean {
		return getPluginScript(pluginId).exists() && getPluginMeta(pluginId).exists()
	}

	suspend fun savePlugin(plugin: LnPlugin, scriptContent: String) = withContext(Dispatchers.IO) {
		val dir = getPluginDir(plugin.id).apply { mkdirs() }
		File(dir, "index.js").writeText(scriptContent, Charsets.UTF_8)
		File(dir, "meta.json").writeText(plugin.toJson().toString(), Charsets.UTF_8)
	}

	suspend fun deletePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
		val dir = getPluginDir(pluginId)
		if (dir.exists()) {
			dir.deleteRecursively()
		} else {
			false
		}
	}

	suspend fun readPluginScript(pluginId: String): String? = withContext(Dispatchers.IO) {
		val scriptFile = getPluginScript(pluginId)
		if (scriptFile.exists()) scriptFile.readText(Charsets.UTF_8) else null
	}

	suspend fun getInstalledPlugins(): List<LnPlugin> = withContext(Dispatchers.IO) {
		val dirs = baseDir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()
		dirs.mapNotNull { dir ->
			val metaFile = File(dir, "meta.json")
			val scriptFile = File(dir, "index.js")
			if (!metaFile.exists() || !scriptFile.exists()) return@mapNotNull null
			runCatching {
				val json = JSONObject(metaFile.readText(Charsets.UTF_8))
				LnPlugin.fromJson(json, isInstalled = true)
			}.getOrNull()
		}
	}
}

