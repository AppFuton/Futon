package io.github.landwarderer.futon.core.exceptions

class SyncApiException(
	message: String,
	val code: Int,
) : RuntimeException(message)
