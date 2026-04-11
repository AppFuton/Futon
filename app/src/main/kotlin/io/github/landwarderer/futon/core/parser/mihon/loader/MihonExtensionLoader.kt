package io.github.landwarderer.futon.core.parser.mihon.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.landwarderer.futon.core.parser.mihon.model.MihonMangaSource

/**
 * Loads and scans installed Mihon (Tachiyomi) extensions from the system.
 */
class MihonExtensionLoader(private val context: Context) {

	private val packageManager = context.packageManager

	/**
	 * Scans all installed packages for Mihon extensions.
	 * An extension is identified by the "tachiyomi.extension" metadata in its manifest.
	 */
	fun loadExtensions(): List<MihonMangaSource> {
		val extensions = mutableListOf<MihonMangaSource>()
		val installedPackages = getInstalledPackages()

		for (pkg in installedPackages) {
			val ai = pkg.applicationInfo ?: continue
			if (ai.metaData == null) continue

			var extensionClass = ai.metaData.get(METADATA_SOURCE_CLASS)?.toString()
			var extensionFactory = ai.metaData.get(METADATA_SOURCE_FACTORY)?.toString()

			if (extensionClass == null && extensionFactory == null) continue

			if (extensionClass != null && extensionClass.startsWith(".")) {
				extensionClass = ai.packageName + extensionClass
			}
			if (extensionFactory != null && extensionFactory.startsWith(".")) {
				extensionFactory = ai.packageName + extensionFactory
			}

			val name = packageManager.getApplicationLabel(ai).toString().replace("Mihon: ", "").replace("Tachiyomi: ", "")
			val libVersion = ai.metaData.get(METADATA_LIB_VERSION)?.toString()

			// Mihon extensions usually have a lib version between 1.2 and 1.9
			if (libVersion != null && !isSupportedLibVersion(libVersion)) {
				continue
			}

			// We don't instantiate the source here, just collect metadata
			// The actual instantiation happens when the repository is created
			extensions.add(
				MihonMangaSource(
					id = ai.packageName.hashCode().toLong(), // Placeholder ID, actual ID comes from source
					title = name,
					packageName = ai.packageName,
					className = extensionClass ?: "",
					factoryClassName = extensionFactory
				)
			)
		}
		return extensions
	}

	private fun getInstalledPackages(): List<PackageInfo> {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
		} else {
			@Suppress("DEPRECATION")
			packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
		}
	}

	private fun isSupportedLibVersion(version: String): Boolean {
		val v = version.toDoubleOrNull() ?: return true
		return v >= 1.2
	}

	companion object {
		private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
		private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
		private const val METADATA_LIB_VERSION = "tachiyomi.extension.lib.version"
	}
}
