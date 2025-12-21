package io.github.landwarderer.futon.settings.utils.validation

import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.EditTextValidator

class PortNumberValidator : EditTextValidator() {

	override fun validate(text: String): ValidationResult {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) {
			return ValidationResult.Success
		}
		return if (!checkCharacters(trimmed)) {
			ValidationResult.Failed(context.getString(R.string.invalid_port_number))
		} else {
			ValidationResult.Success
		}
	}

	private fun checkCharacters(value: String): Boolean {
		val intValue = value.toIntOrNull() ?: return false
		return intValue in 1..65535
	}
}
