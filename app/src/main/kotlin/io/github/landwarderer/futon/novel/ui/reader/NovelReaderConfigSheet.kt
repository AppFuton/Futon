package io.github.landwarderer.futon.novel.ui.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.SheetNovelReaderConfigBinding
import io.github.landwarderer.futon.novel.data.model.NovelReadingMode
import io.github.landwarderer.futon.novel.data.model.NovelTextAlign
import io.github.landwarderer.futon.novel.data.model.NovelTheme

class NovelReaderConfigSheet : BottomSheetDialogFragment() {

	interface Callback {
		fun onNovelSettingsChanged(settings: NovelReaderSettings)
	}

	private var _binding: SheetNovelReaderConfigBinding? = null
	private val binding get() = _binding!!

	private lateinit var currentSettings: NovelReaderSettings
	private var callback: Callback? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = SheetNovelReaderConfigBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		currentSettings = NovelReaderSettings.load(requireContext())
		callback = activity as? Callback ?: parentFragment as? Callback

		bindCurrentSettings()
		setupListeners()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		NovelReaderSettings.save(requireContext(), currentSettings)
		_binding = null
	}

	@SuppressLint("SetTextI18n")
	private fun bindCurrentSettings() {
		// Theme
		when (currentSettings.theme) {
			NovelTheme.DARK -> binding.chipThemeDark.isChecked = true
			NovelTheme.LIGHT -> binding.chipThemeLight.isChecked = true
			NovelTheme.OLED -> binding.chipThemeOled.isChecked = true
			NovelTheme.SEPIA -> binding.chipThemeSepia.isChecked = true
			NovelTheme.CUSTOM -> {}
		}

		// Reading Mode
		when (currentSettings.readingMode) {
			NovelReadingMode.CONTINUOUS_SCROLL -> binding.buttonModeContinuous.isChecked = true
			NovelReadingMode.PAGED -> binding.buttonModePaged.isChecked = true
		}

		// Font Size
		binding.sliderFontSize.value = currentSettings.fontSizeSp.coerceIn(12f, 36f)
		binding.textFontSizeValue.text = "${currentSettings.fontSizeSp.toInt()}sp"

		// Font Family
		when (currentSettings.fontFamily) {
			"serif" -> binding.chipFontSerif.isChecked = true
			"sans-serif" -> binding.chipFontSans.isChecked = true
			"monospace" -> binding.chipFontMono.isChecked = true
			else -> binding.chipFontSerif.isChecked = true
		}

		// Line Spacing
		binding.sliderLineSpacing.value = currentSettings.lineSpacingMultiplier.coerceIn(1.0f, 2.4f)
		binding.textLineSpacingValue.text = String.format("%.1f", currentSettings.lineSpacingMultiplier)

		// Text Alignment
		when (currentSettings.textAlignment) {
			NovelTextAlign.LEFT -> binding.buttonAlignLeft.isChecked = true
			NovelTextAlign.JUSTIFY -> binding.buttonAlignJustify.isChecked = true
		}
	}

	@SuppressLint("SetTextI18n")
	private fun setupListeners() {
		binding.chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
			val newTheme = when {
				checkedIds.contains(R.id.chip_theme_light) -> NovelTheme.LIGHT
				checkedIds.contains(R.id.chip_theme_oled) -> NovelTheme.OLED
				checkedIds.contains(R.id.chip_theme_sepia) -> NovelTheme.SEPIA
				else -> NovelTheme.DARK
			}
			updateSettings { copy(theme = newTheme) }
		}

		binding.toggleGroupReadingMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (isChecked) {
				val newMode = when (checkedId) {
					R.id.button_mode_paged -> NovelReadingMode.PAGED
					else -> NovelReadingMode.CONTINUOUS_SCROLL
				}
				updateSettings { copy(readingMode = newMode) }
			}
		}

		binding.sliderFontSize.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				binding.textFontSizeValue.text = "${value.toInt()}sp"
				updateSettings { copy(fontSizeSp = value) }
			}
		}

		binding.chipGroupFontFamily.setOnCheckedStateChangeListener { _, checkedIds ->
			val newFont = when {
				checkedIds.contains(R.id.chip_font_sans) -> "sans-serif"
				checkedIds.contains(R.id.chip_font_mono) -> "monospace"
				else -> "serif"
			}
			updateSettings { copy(fontFamily = newFont) }
		}

		binding.sliderLineSpacing.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				binding.textLineSpacingValue.text = String.format("%.1f", value)
				updateSettings { copy(lineSpacingMultiplier = value) }
			}
		}

		binding.toggleGroupAlignment.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (isChecked) {
				val newAlign = when (checkedId) {
					R.id.button_align_justify -> NovelTextAlign.JUSTIFY
					else -> NovelTextAlign.LEFT
				}
				updateSettings { copy(textAlignment = newAlign) }
			}
		}
	}

	private fun updateSettings(transform: NovelReaderSettings.() -> NovelReaderSettings) {
		currentSettings = currentSettings.transform()
		callback?.onNovelSettingsChanged(currentSettings)
	}

	companion object {
		const val TAG = "NovelReaderConfigSheet"

		fun newInstance(): NovelReaderConfigSheet = NovelReaderConfigSheet()
	}
}
