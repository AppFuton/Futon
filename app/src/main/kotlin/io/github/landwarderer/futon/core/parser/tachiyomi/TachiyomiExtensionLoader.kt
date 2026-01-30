package io.github.landwarderer.futon.core.parser.tachiyomi

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TachiyomiExtensionLoader @Inject constructor(
	@ApplicationContext private val context: Context,
	private val scanner: TachiyomiExtensionScanner,
) {

	suspend fun loadExtensions(): List<ExtensionLoadResult> = withContext(Dispatchers.Default) {
		val extensionPackages = scanner.scanInstalledExtensions()

		val deferred = extensionPackages.map { extPkgInfo ->
			async { loadExtension(extPkgInfo) }
		}
		deferred.awaitAll()
	}

	internal suspend fun loadExtension(extPkgInfo: ExtensionPackageInfo): ExtensionLoadResult =
		withContext(Dispatchers.Default) {
			runCatching {
				val pkgInfo = extPkgInfo.packageInfo
				val appInfo = pkgInfo.applicationInfo
					?: return@withContext ExtensionLoadResult.Error

				val pkgName = pkgInfo.packageName
				val versionName = pkgInfo.versionName ?: return@withContext ExtensionLoadResult.Error
				val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

				val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
				if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
					return@withContext ExtensionLoadResult.InvalidLibVersion(pkgName, libVersion)
				}

				val extName = context.packageManager.getApplicationLabel(appInfo).toString()
					.removePrefix("Tachiyomi: ")

				val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1

				val sourceClassNames = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
					?.split(";")
					?.map { it.trim() }
					?: return@withContext ExtensionLoadResult.Error

				val classLoader = try {
					ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
				} catch (e: Exception) {
					e.printStackTraceDebug()
					return@withContext ExtensionLoadResult.Error
				}

				val sources = sourceClassNames.flatMap { className ->
					try {
						val fullClassName = if (className.startsWith(".")) {
							pkgName + className
						} else {
							className
						}

						val sourceClass = Class.forName(fullClassName, false, classLoader)
						val sourceInstance = sourceClass.getDeclaredConstructor().newInstance()

						when {
							isSourceInstance(sourceInstance) -> {
								listOf(createTachiyomiSource(sourceInstance))
							}
							isSourceFactoryInstance(sourceInstance) -> {
								val factoryMethod = sourceClass.getMethod("createSources")
								val sourcesFromFactory = factoryMethod.invoke(sourceInstance) as? List<*>
									?: emptyList()
								sourcesFromFactory.mapNotNull { src ->
									if (src != null && isSourceInstance(src)) {
										createTachiyomiSource(src)
									} else {
										null
									}
								}
							}
							else -> emptyList()
						}
					} catch (e: Exception) {
						e.printStackTraceDebug()
						emptyList()
					}
				}

				if (sources.isEmpty()) {
					return@withContext ExtensionLoadResult.Error
				}

				val icon = try {
					context.packageManager.getApplicationIcon(appInfo)
				} catch (e: Exception) {
					null
				}

				val extension = TachiyomiExtension(
					pkgName = pkgName,
					name = extName,
					versionName = versionName,
					versionCode = versionCode,
					libVersion = libVersion,
					isNsfw = isNsfw,
					sources = sources,
					icon = icon,
					isShared = extPkgInfo.isShared,
					hasUpdate = false,
				)

				ExtensionLoadResult.Success(extension)
			}.getOrElse { error ->
				error.printStackTraceDebug()
				ExtensionLoadResult.Error
			}
		}

	private fun isSourceInstance(obj: Any): Boolean {
		return try {
			val sourceInterface = obj.javaClass.classLoader?.loadClass(SOURCE_CLASS_NAME)
			sourceInterface?.isInstance(obj) == true
		} catch (e: Exception) {
			false
		}
	}

	private fun isSourceFactoryInstance(obj: Any): Boolean {
		return try {
			val factoryInterface = obj.javaClass.classLoader?.loadClass(SOURCE_FACTORY_CLASS_NAME)
			factoryInterface?.isInstance(obj) == true
		} catch (e: Exception) {
			false
		}
	}

	private fun createTachiyomiSource(sourceInstance: Any): TachiyomiSource {
		val sourceClass = sourceInstance.javaClass

		val id = try {
			sourceClass.getMethod("getId").invoke(sourceInstance) as Long
		} catch (e: Exception) {
			0L
		}

		val name = try {
			sourceClass.getMethod("getName").invoke(sourceInstance) as String
		} catch (e: Exception) {
			"Unknown"
		}

		val lang = try {
			sourceClass.getMethod("getLang").invoke(sourceInstance) as? String ?: ""
		} catch (e: Exception) {
			""
		}

		val baseUrl = try {
			sourceClass.getMethod("getBaseUrl").invoke(sourceInstance) as? String
		} catch (e: Exception) {
			null
		}

		val versionId = try {
			sourceClass.getMethod("getVersionId").invoke(sourceInstance) as? Int ?: 1
		} catch (e: Exception) {
			1
		}

		return TachiyomiSource(
			id = id,
			name = name,
			lang = lang,
			baseUrl = baseUrl,
			versionId = versionId,
			sourceInstance = sourceInstance,
		)
	}

	companion object {
		private const val LIB_VERSION_MIN = 1.4
		private const val LIB_VERSION_MAX = 1.5

		private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
		private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

		private const val SOURCE_CLASS_NAME = "eu.kanade.tachiyomi.source.Source"
		private const val SOURCE_FACTORY_CLASS_NAME = "eu.kanade.tachiyomi.source.SourceFactory"
	}
}

private class ChildFirstPathClassLoader(
	dexPath: String,
	librarySearchPath: String?,
	parent: ClassLoader?,
) : dalvik.system.PathClassLoader(dexPath, librarySearchPath, parent) {

	@Throws(ClassNotFoundException::class)
	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		var c = findLoadedClass(name)

		if (c == null) {
			try {
				c = findClass(name)
			} catch (e: ClassNotFoundException) {
			}

			if (c == null) {
				c = super.loadClass(name, resolve)
			}
		}

		return c
	}
}
