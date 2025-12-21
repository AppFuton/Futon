package io.github.landwarderer.futon.reader.ui

import com.google.android.material.slider.LabelFormatter
import io.github.landwarderer.futon.parsers.util.format

class PageLabelFormatter : LabelFormatter {

	override fun getFormattedValue(value: Float): String {
		return (value + 1).format(0)
	}
}
