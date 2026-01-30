package io.github.landwarderer.futon.core.parser.tachiyomi

import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable

/**
 * Represents a Tachiyomi extension loaded from an installed APK.
 *
 * @property pkgName The package name of the extension (e.g., "eu.kanade.tachiyomi.extension.en.mangadex")
 * @property name The display name of the extension (e.g., "MangaDex")
 * @property versionName The version name (e.g., "1.5.7")
 * @property versionCode The version code as a long
 * @property libVersion The extension library version (1.4 or 1.5)
 * @property isNsfw Whether this extension provides NSFW content
 * @property sources List of manga sources provided by this extension
 * @property icon The extension's icon drawable
 * @property isShared Whether this is a system-installed extension (true) or private extension (false)
 * @property hasUpdate Whether an update is available for this extension
 * @property isEnabled Whether this extension is currently enabled
 */
data class TachiyomiExtension(
	val pkgName: String,
	val name: String,
	val versionName: String,
	val versionCode: Long,
	val libVersion: Double,
	val isNsfw: Boolean,
	val sources: List<TachiyomiSource>,
	val icon: Drawable?,
	val isShared: Boolean,
	val hasUpdate: Boolean = false,
	val isEnabled: Boolean = true,
)

/**
 * Represents a manga source within a Tachiyomi extension.
 * Multiple sources can exist in a single extension (via SourceFactory).
 *
 * @property id The unique source ID (generated from name/lang/versionId)
 * @property name The source name (e.g., "MangaDex")
 * @property lang ISO 639-1 language code (e.g., "en", "ja", "all")
 * @property baseUrl The base URL of the source
 * @property versionId The source version ID used in ID generation
 * @property sourceInstance The loaded Source instance (lazy loaded)
 */
data class TachiyomiSource(
	val id: Long,
	val name: String,
	val lang: String,
	val baseUrl: String? = null,
	val versionId: Int = 1,
	var sourceInstance: Any? = null,
) {
	val displayName: String
		get() = if (lang == "all") name else "$name ($lang)"
}

/**
 * Metadata for a Tachiyomi extension from the keiyoushi repository index.
 * Used for browsing and installing extensions from the remote repository.
 *
 * @property name The extension name with prefix (e.g., "Tachiyomi: MangaDex")
 * @property pkgName The package name
 * @property apkName The APK filename in the repository
 * @property lang The primary language code
 * @property versionCode The version code
 * @property versionName The version name
 * @property isNsfw Whether this extension is NSFW
 * @property sources List of source metadata from repository index
 * @property iconUrl URL to the extension icon
 */
data class TachiyomiExtensionMetadata(
	val name: String,
	val pkgName: String,
	val apkName: String,
	val lang: String,
	val versionCode: Int,
	val versionName: String,
	val isNsfw: Boolean,
	val sources: List<TachiyomiSourceMetadata>,
	val iconUrl: String? = null,
)

/**
 * Source metadata from repository index.
 */
data class TachiyomiSourceMetadata(
	val id: String,
	val name: String,
	val lang: String,
	val baseUrl: String,
	val versionId: Int = 1,
)

/**
 * Result of attempting to load a Tachiyomi extension.
 */
sealed class ExtensionLoadResult {
	data class Success(val extension: TachiyomiExtension) : ExtensionLoadResult()
	data class Untrusted(val extension: TachiyomiExtension) : ExtensionLoadResult()
	data object Error : ExtensionLoadResult()
	data class InvalidLibVersion(val pkgName: String, val version: Double?) : ExtensionLoadResult()
}

/**
 * Information about a package that might be a Tachiyomi extension.
 */
internal data class ExtensionPackageInfo(
	val packageInfo: PackageInfo,
	val isShared: Boolean,
)
