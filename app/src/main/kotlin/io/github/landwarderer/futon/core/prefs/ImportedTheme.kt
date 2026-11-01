package io.github.landwarderer.futon.core.prefs

import org.json.JSONObject
import java.util.Locale

/** A validated Idle Fantasy-compatible theme with its original definition retained for storage. */
data class ImportedTheme(
	val id: String,
	val displayName: String,
	val isDark: Boolean,
	val primaryColor: Int,
	val onPrimaryColor: Int?,
	val json: String,
)

object ImportedThemeFile {
	const val MAX_FILE_SIZE = 256 * 1024
	private val ID_SANITIZATION_REGEX = Regex("[^\\p{L}\\p{N}]+")
	private val HEX_COLOR_REGEX = Regex("^(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

	fun parseImport(source: String): List<ImportedTheme> {
		require(source.length <= MAX_FILE_SIZE) { "Theme file is too large" }
		return parse(JSONObject(source), requireNonEmpty = true)
	}

	fun parseStored(source: String): List<ImportedTheme> = runCatching {
		parse(JSONObject(source), requireNonEmpty = false)
	}.getOrDefault(emptyList())

	fun mergeStored(current: String, imported: List<ImportedTheme>): String {
		val root = runCatching { JSONObject(current) }.getOrElse { JSONObject() }
		for (theme in imported) {
			val obj = JSONObject(theme.json)
			obj.put("display_name", theme.displayName)
			root.put(theme.id, obj)
		}
		return root.toString()
	}

	private fun parse(root: JSONObject, requireNonEmpty: Boolean): List<ImportedTheme> {
		val result = ArrayList<ImportedTheme>(root.length())
		val keys = root.keys()
		while (keys.hasNext()) {
			val sourceName = keys.next()
			val theme = root.optJSONObject(sourceName) ?: continue
			val id = sourceName.trim().lowercase(Locale.ROOT)
				.replace(ID_SANITIZATION_REGEX, "_")
				.trim('_')
			require(id.isNotBlank() && id.length <= 80) { "Theme name is missing or too long" }

			val base = theme.optString("base").lowercase(Locale.ROOT)
			require(base == "light" || base == "dark") { "Theme '$sourceName' has an unsupported base" }
			val colours = theme.optJSONObject("colours") ?: throw IllegalArgumentException("Theme '$sourceName' has no colours")
			val scheme = theme.optJSONObject("colour_scheme") ?: throw IllegalArgumentException("Theme '$sourceName' has no colour_scheme")
			require(colours.length() in 1..100) { "Theme '$sourceName' has no usable colours" }
			require(scheme.length() in 1..100) { "Theme '$sourceName' has no colour roles" }

			val parsedColours = HashMap<String, Int>(colours.length())
			val colourNames = colours.keys()
			while (colourNames.hasNext()) {
				val name = colourNames.next()
				parsedColours[name] = parseArgb(colours.getString(name))
			}

			val roleNames = HashMap<String, String>(scheme.length())
			val roles = scheme.keys()
			while (roles.hasNext()) {
				val role = roles.next()
				val colourName = scheme.getString(role)
				require(parsedColours.containsKey(colourName)) {
					"Theme '$sourceName' refers to missing colour '$colourName'"
				}
				roleNames[role] = colourName
			}
			val primaryName = roleNames["primary"] ?: roleNames["colorPrimary"]
				?: throw IllegalArgumentException("Theme '$sourceName' has no primary colour")
			val onPrimary = (roleNames["on_primary"] ?: roleNames["onPrimary"])?.let(parsedColours::get)
			val displayName = theme.optString("display_name").trim().ifEmpty {
				sourceName.replace('_', ' ').replaceFirstChar { it.uppercase() }
			}
			theme.put("display_name", displayName)

			result += ImportedTheme(
				id = id,
				displayName = displayName,
				isDark = base == "dark",
				primaryColor = parsedColours.getValue(primaryName),
				onPrimaryColor = onPrimary,
				json = theme.toString(),
			)
		}
		require(!requireNonEmpty || result.isNotEmpty()) { "Theme file contains no themes" }
		return result
	}

	private fun parseArgb(value: String): Int {
		val digits = value.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")
		require(HEX_COLOR_REGEX.matches(digits)) {
			"Invalid hex colour value: $value"
		}
		val fullHex = when (digits.length) {
			3 -> "FF${digits[0]}${digits[0]}${digits[1]}${digits[1]}${digits[2]}${digits[2]}"
			6 -> "FF$digits"
			else -> digits
		}
		return fullHex.toLong(16).toInt()
	}
}
