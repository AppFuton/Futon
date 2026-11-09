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

	companion object {
		private val VALID_PLUGIN_ID_REGEX = Regex("^[A-Za-z0-9._-]+$")

		fun isValidPluginId(pluginId: String): Boolean {
			if (pluginId.isEmpty() || pluginId == "." || pluginId == "..") return false
			return VALID_PLUGIN_ID_REGEX.matches(pluginId)
		}
	}

	fun getPluginDir(pluginId: String): File {
		require(isValidPluginId(pluginId)) { "Invalid plugin ID: $pluginId" }
		return File(baseDir, pluginId)
	}

	fun getPluginScript(pluginId: String): File = File(getPluginDir(pluginId), "index.js")

	private fun getPluginMeta(pluginId: String): File = File(getPluginDir(pluginId), "meta.json")

	fun isPluginInstalled(pluginId: String): Boolean {
		if (!isValidPluginId(pluginId)) return false
		return getPluginScript(pluginId).exists() && getPluginMeta(pluginId).exists()
	}

	suspend fun savePlugin(plugin: LnPlugin, scriptContent: String) = withContext(Dispatchers.IO) {
		require(isValidPluginId(plugin.id)) { "Invalid plugin ID: ${plugin.id}" }
		val dir = getPluginDir(plugin.id).apply { mkdirs() }

		val tempScriptFile = File(dir, "index.js.tmp")
		val targetScriptFile = File(dir, "index.js")
		tempScriptFile.writeText(scriptContent, Charsets.UTF_8)
		if (!tempScriptFile.renameTo(targetScriptFile)) {
			tempScriptFile.copyTo(targetScriptFile, overwrite = true)
			tempScriptFile.delete()
		}

		val tempMetaFile = File(dir, "meta.json.tmp")
		val targetMetaFile = File(dir, "meta.json")
		tempMetaFile.writeText(plugin.toJson().toString(), Charsets.UTF_8)
		if (!tempMetaFile.renameTo(targetMetaFile)) {
			tempMetaFile.copyTo(targetMetaFile, overwrite = true)
			tempMetaFile.delete()
		}
	}

	suspend fun deletePlugin(pluginId: String): Boolean = withContext(Dispatchers.IO) {
		if (!isValidPluginId(pluginId)) return@withContext false
		val dir = getPluginDir(pluginId)
		if (dir.exists()) {
			dir.deleteRecursively()
		} else {
			false
		}
	}

	suspend fun readPluginScript(pluginId: String): String? = withContext(Dispatchers.IO) {
		if (!isValidPluginId(pluginId)) return@withContext null
		val scriptFile = getPluginScript(pluginId)
		if (scriptFile.exists()) scriptFile.readText(Charsets.UTF_8) else null
	}

	suspend fun getInstalledPlugins(): List<LnPlugin> = withContext(Dispatchers.IO) {
		getInstalledPluginsSync()
	}

	fun getInstalledPluginsSync(): List<LnPlugin> {
		val dirs = baseDir.listFiles { f -> f.isDirectory } ?: return emptyList()
		return dirs.mapNotNull { dir ->
			if (!isValidPluginId(dir.name)) return@mapNotNull null
			val metaFile = File(dir, "meta.json")
			val scriptFile = File(dir, "index.js")
			if (!metaFile.exists() || !scriptFile.exists()) return@mapNotNull null
			runCatching {
				val json = JSONObject(metaFile.readText(Charsets.UTF_8))
				LnPlugin.fromJson(json, isInstalled = true)
			}.getOrNull()
		}
	}

	fun getPlugin(pluginId: String): LnPlugin? {
		if (!isValidPluginId(pluginId)) return null
		val dir = getPluginDir(pluginId)
		val metaFile = File(dir, "meta.json")
		val scriptFile = File(dir, "index.js")
		if (!metaFile.exists() || !scriptFile.exists()) return null
		return runCatching {
			val json = JSONObject(metaFile.readText(Charsets.UTF_8))
			LnPlugin.fromJson(json, isInstalled = true)
		}.getOrNull()
	}
}
