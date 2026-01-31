package io.github.landwarderer.futon.core.parser.tachiyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TachiyomiExtensionScanner @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val pkgManager: PackageManager = context.packageManager

	internal fun scanInstalledExtensions(): List<ExtensionPackageInfo> {
		val sharedExtensions = scanSharedExtensions()
		val privateExtensions = scanPrivateExtensions()

		return mergeAndDeduplicateExtensions(sharedExtensions, privateExtensions)
	}

	internal fun scanSharedExtensions(): List<ExtensionPackageInfo> {
		val installedPackages = try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
			} else {
				@Suppress("DEPRECATION")
				pkgManager.getInstalledPackages(PACKAGE_FLAGS)
			}
		} catch (e: Exception) {
			e.printStackTraceDebug()
			return emptyList()
		}

		android.util.Log.d("TachiyomiExtScanner", "Found ${installedPackages.size} total installed packages")
		
		val extensions = installedPackages
			.filter { isPackageAnExtension(it) }
			.map { ExtensionPackageInfo(packageInfo = it, isShared = true) }
		
		android.util.Log.d("TachiyomiExtScanner", "Found ${extensions.size} Tachiyomi extensions")
		extensions.forEach { ext ->
			android.util.Log.d("TachiyomiExtScanner", "Extension: ${ext.packageInfo.packageName}")
		}
		
		return extensions
	}

	internal fun scanPrivateExtensions(): List<ExtensionPackageInfo> {
		val privateExtDir = getPrivateExtensionDir()
		if (!privateExtDir.exists()) {
			return emptyList()
		}

		return privateExtDir.listFiles()
			?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
			?.mapNotNull { file ->
				try {
					val path = file.absolutePath
					val pkgInfo = pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
					pkgInfo?.apply {
						applicationInfo?.fixBasePaths(path)
					}?.takeIf { isPackageAnExtension(it) }
						?.let { ExtensionPackageInfo(packageInfo = it, isShared = false) }
				} catch (e: Exception) {
					e.printStackTraceDebug()
					null
				}
			} ?: emptyList()
	}

	fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
		// Primary check: uses-feature declaration
		val hasFeature = pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
		if (hasFeature) {
			return true
		}
		
		// Fallback check: package name pattern
		// Tachiyomi extensions follow the pattern: eu.kanade.tachiyomi.extension.*
		val pkgName = pkgInfo.packageName
		if (pkgName.startsWith("eu.kanade.tachiyomi.extension.") || 
		    pkgName.startsWith("eu.mihon.extension.")) {
			android.util.Log.d("TachiyomiExtScanner", "Detected extension by package name: $pkgName")
			return true
		}
		
		// Additional check: has tachiyomi.extension.class metadata
		val hasMetadata = pkgInfo.applicationInfo?.metaData?.containsKey("tachiyomi.extension.class") == true
		if (hasMetadata) {
			android.util.Log.d("TachiyomiExtScanner", "Detected extension by metadata: $pkgName")
			return true
		}
		
		return false
	}

	fun getPrivateExtensionDir(): File = File(context.filesDir, "tachiyomi_exts")

	private fun mergeAndDeduplicateExtensions(
		shared: List<ExtensionPackageInfo>,
		private: List<ExtensionPackageInfo>,
	): List<ExtensionPackageInfo> {
		val sharedByPkg = shared.associateBy { it.packageInfo.packageName }
		val privateByPkg = private.associateBy { it.packageInfo.packageName }

		val allPackageNames = (sharedByPkg.keys + privateByPkg.keys).distinct()

		return allPackageNames.mapNotNull { pkgName ->
			val sharedPkg = sharedByPkg[pkgName]
			val privatePkg = privateByPkg[pkgName]

			when {
				sharedPkg != null && privatePkg != null -> {
					val sharedVersion = PackageInfoCompat.getLongVersionCode(sharedPkg.packageInfo)
					val privateVersion = PackageInfoCompat.getLongVersionCode(privatePkg.packageInfo)
					if (privateVersion >= sharedVersion) privatePkg else sharedPkg
				}
				sharedPkg != null -> sharedPkg
				privatePkg != null -> privatePkg
				else -> null
			}
		}
	}

	private fun android.content.pm.ApplicationInfo.fixBasePaths(apkPath: String) {
		sourceDir = apkPath
		publicSourceDir = apkPath
	}

	companion object {
		private const val EXTENSION_FEATURE = "tachiyomi.extension"
		private const val PRIVATE_EXTENSION_EXTENSION = "ext"

		@Suppress("DEPRECATION")
		private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
			PackageManager.GET_META_DATA or
			PackageManager.GET_SIGNATURES or
			(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				PackageManager.GET_SIGNING_CERTIFICATES
			} else {
				0
			})
	}
}
