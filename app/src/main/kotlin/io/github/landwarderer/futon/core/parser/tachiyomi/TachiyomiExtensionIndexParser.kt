package io.github.landwarderer.futon.core.parser.tachiyomi

import io.github.landwarderer.futon.core.network.MangaHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and parses the Tachiyomi extension index from the keiyoushi repository.
 */
@Singleton
class TachiyomiExtensionIndexParser @Inject constructor(
	@MangaHttpClient private val okHttpClient: OkHttpClient,
) {

	/**
	 * Fetches the extension index from keiyoushi repository.
	 * @return List of available extensions or empty list on error
	 */
	suspend fun fetchExtensions(): List<TachiyomiExtensionMetadata> = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(INDEX_URL)
				.get()
				.build()

			okHttpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					return@withContext emptyList()
				}

				val jsonString = response.body?.string() ?: return@withContext emptyList()
				parseExtensionIndex(jsonString)
			}
		}.getOrElse { emptyList() }
	}

	/**
	 * Parses the JSON extension index into metadata objects.
	 */
	private fun parseExtensionIndex(json: String): List<TachiyomiExtensionMetadata> {
		val extensions = mutableListOf<TachiyomiExtensionMetadata>()
		val jsonArray = JSONArray(json)

		for (i in 0 until jsonArray.length()) {
			runCatching {
				val obj = jsonArray.getJSONObject(i)

				// Parse sources array
				val sources = mutableListOf<TachiyomiSourceMetadata>()
				val sourcesArray = obj.getJSONArray("sources")
				for (j in 0 until sourcesArray.length()) {
					val sourceObj = sourcesArray.getJSONObject(j)
					sources.add(
						TachiyomiSourceMetadata(
							id = sourceObj.getString("id"),
							name = sourceObj.getString("name"),
							lang = sourceObj.getString("lang"),
							baseUrl = sourceObj.optString("baseUrl", ""),
							versionId = sourceObj.optInt("versionId", 1),
						),
					)
				}

				val pkgName = obj.getString("pkg")
				val apkName = obj.getString("apk")

				extensions.add(
					TachiyomiExtensionMetadata(
						name = obj.getString("name"),
						pkgName = pkgName,
						apkName = apkName,
						lang = obj.getString("lang"),
						versionCode = obj.getInt("code"),
						versionName = obj.getString("version"),
						isNsfw = obj.optInt("nsfw", 0) == 1,
						sources = sources,
						iconUrl = buildIconUrl(pkgName, apkName),
					),
				)
			}
		}

		return extensions
	}

	/**
	 * Builds the icon URL for an extension.
	 */
	private fun buildIconUrl(pkgName: String, apkName: String): String {
		// Icon path follows pattern: apks/<lang>/<source>/icon.png
		// Extract from APK name: tachiyomi-<lang>.<source>-v<version>.apk
		val baseName = apkName.removeSuffix(".apk")
		val parts = baseName.removePrefix("tachiyomi-").split("-")
		if (parts.size >= 1) {
			val langAndSource = parts[0].split(".")
			if (langAndSource.size == 2) {
				val lang = langAndSource[0]
				val source = langAndSource[1]
				return "$ICON_BASE_URL/$lang/$source/icon.png"
			}
		}
		return ""
	}

	companion object {
		private const val INDEX_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
		private const val ICON_BASE_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apks"
		const val APK_BASE_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apks"
	}
}
