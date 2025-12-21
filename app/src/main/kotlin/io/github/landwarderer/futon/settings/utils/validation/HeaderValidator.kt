package io.github.landwarderer.futon.settings.utils.validation

import okhttp3.Headers
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.network.CommonHeaders
import io.github.landwarderer.futon.core.util.EditTextValidator

class HeaderValidator : EditTextValidator() {

	private val headers = Headers.Builder()

	override fun validate(text: String): ValidationResult {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) {
			return ValidationResult.Success
		}
		return if (!validateImpl(trimmed)) {
			ValidationResult.Failed(context.getString(R.string.invalid_value_message))
		} else {
			ValidationResult.Success
		}
	}

	private fun validateImpl(value: String): Boolean = runCatching {
		headers[CommonHeaders.USER_AGENT] = value
	}.isSuccess
}
