package io.github.landwarderer.futon.novel.data.lnreader.model

import org.json.JSONObject

/**
 * Model representing an LNReader remote or installed plugin.
 * Mirrors the schema in LNReader's `plugins.min.json`.
 */
data class LnPlugin(
	val id: String,
	val name: String,
	val site: String,
	val lang: String,
	val version: String,
	val url: String,
	val iconUrl: String,
	val customJsUrl: String? = null,
	val customCssUrl: String? = null,
	val isInstalled: Boolean = false,
	val hasUpdate: Boolean = false,
) {
	companion object {
		fun fromJson(json: JSONObject, isInstalled: Boolean = false, hasUpdate: Boolean = false): LnPlugin {
			return LnPlugin(
				id = json.getString("id"),
				name = json.getString("name"),
				site = json.optString("site", ""),
				lang = json.optString("lang", "en"),
				version = json.optString("version", "1.0.0"),
				url = json.getString("url"),
				iconUrl = json.optString("iconUrl", ""),
				customJsUrl = json.optString("customJS").takeIf { !it.isNullOrBlank() },
				customCssUrl = json.optString("customCSS").takeIf { !it.isNullOrBlank() },
				isInstalled = isInstalled,
				hasUpdate = hasUpdate,
			)
		}
	}

	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("name", name)
		put("site", site)
		put("lang", lang)
		put("version", version)
		put("url", url)
		put("iconUrl", iconUrl)
		customJsUrl?.let { put("customJS", it) }
		customCssUrl?.let { put("customCSS", it) }
	}
}

/**
 * Novel summary returned by `plugin.popularNovels()` or `plugin.searchNovels()`.
 */
data class LnNovelItem(
	val name: String,
	val path: String,
	val cover: String?,
)

/**
 * Chapter item returned by `plugin.parseNovel()`.
 */
data class LnChapterItem(
	val name: String,
	val path: String,
	val chapterNumber: Float?,
	val releaseTime: String?,
)

/**
 * Full novel details returned by `plugin.parseNovel()`.
 */
data class LnSourceNovel(
	val name: String,
	val path: String,
	val cover: String?,
	val author: String?,
	val artist: String?,
	val status: String?,
	val summary: String?,
	val genres: String?,
	val chapters: List<LnChapterItem>,
)

