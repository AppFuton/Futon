package io.github.landwarderer.futon.novel.ui.reader

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.landwarderer.futon.core.util.ext.getEnumValue
import io.github.landwarderer.futon.core.util.ext.putEnumValue
import io.github.landwarderer.futon.novel.data.model.NovelReadingMode
import io.github.landwarderer.futon.novel.data.model.NovelTextAlign
import io.github.landwarderer.futon.novel.data.model.NovelTheme

data class NovelReaderSettings(
	val fontSizeSp: Float = 18f,
	val theme: NovelTheme = NovelTheme.DARK,
	val customBackgroundColor: Int = Color.parseColor("#141414"),
	val customTextColor: Int = Color.parseColor("#E0E0E0"),
	val fontFamily: String = "serif",
	val lineSpacingMultiplier: Float = 1.6f,
	val paragraphMarginEm: Float = 1.0f,
	val pageMarginDp: Int = 18,
	val textAlignment: NovelTextAlign = NovelTextAlign.LEFT,
	val readingMode: NovelReadingMode = NovelReadingMode.CONTINUOUS_SCROLL,
) {
	val effectiveBackgroundColor: Int
		get() = if (theme == NovelTheme.CUSTOM) customBackgroundColor else theme.backgroundColor

	val effectiveTextColor: Int
		get() = if (theme == NovelTheme.CUSTOM) customTextColor else theme.textColor

	fun toCssVariables(): String {
		val bgHex = String.format("#%06X", 0xFFFFFF and effectiveBackgroundColor)
		val textHex = String.format("#%06X", 0xFFFFFF and effectiveTextColor)
		return """
			:root {
				--reader-bg: $bgHex;
				--reader-text: $textHex;
				--reader-font-size: ${fontSizeSp.toInt()}px;
				--reader-font-family: $fontFamily, serif;
				--reader-line-height: $lineSpacingMultiplier;
				--reader-paragraph-margin: ${paragraphMarginEm}em;
				--reader-page-margin: ${pageMarginDp}px;
				--reader-text-align: ${textAlignment.cssValue};
			}
		""".trimIndent()
	}

	companion object {
		private const val KEY_FONT_SIZE = "novel_font_size"
		private const val KEY_THEME = "novel_theme"
		private const val KEY_BG_COLOR = "novel_bg_color"
		private const val KEY_TEXT_COLOR = "novel_text_color"
		private const val KEY_FONT_FAMILY = "novel_font_family"
		private const val KEY_LINE_SPACING = "novel_line_spacing"
		private const val KEY_PARA_MARGIN = "novel_para_margin"
		private const val KEY_PAGE_MARGIN = "novel_page_margin"
		private const val KEY_TEXT_ALIGN = "novel_text_align"
		private const val KEY_READING_MODE = "novel_reading_mode"

		fun load(context: Context): NovelReaderSettings {
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			return NovelReaderSettings(
				fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 18f),
				theme = prefs.getEnumValue(KEY_THEME, NovelTheme.DARK),
				customBackgroundColor = prefs.getInt(KEY_BG_COLOR, Color.parseColor("#141414")),
				customTextColor = prefs.getInt(KEY_TEXT_COLOR, Color.parseColor("#E0E0E0")),
				fontFamily = prefs.getString(KEY_FONT_FAMILY, "serif") ?: "serif",
				lineSpacingMultiplier = prefs.getFloat(KEY_LINE_SPACING, 1.6f),
				paragraphMarginEm = prefs.getFloat(KEY_PARA_MARGIN, 1.0f),
				pageMarginDp = prefs.getInt(KEY_PAGE_MARGIN, 18),
				textAlignment = prefs.getEnumValue(KEY_TEXT_ALIGN, NovelTextAlign.LEFT),
				readingMode = prefs.getEnumValue(KEY_READING_MODE, NovelReadingMode.CONTINUOUS_SCROLL),
			)
		}

		fun save(context: Context, settings: NovelReaderSettings) {
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			prefs.edit {
				putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
				putEnumValue(KEY_THEME, settings.theme)
				putInt(KEY_BG_COLOR, settings.customBackgroundColor)
				putInt(KEY_TEXT_COLOR, settings.customTextColor)
				putString(KEY_FONT_FAMILY, settings.fontFamily)
				putFloat(KEY_LINE_SPACING, settings.lineSpacingMultiplier)
				putFloat(KEY_PARA_MARGIN, settings.paragraphMarginEm)
				putInt(KEY_PAGE_MARGIN, settings.pageMarginDp)
				putEnumValue(KEY_TEXT_ALIGN, settings.textAlignment)
				putEnumValue(KEY_READING_MODE, settings.readingMode)
			}
		}
	}
}
