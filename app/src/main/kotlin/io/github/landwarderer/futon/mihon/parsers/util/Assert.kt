package io.github.landwarderer.futon.mihon.parsers.util

internal fun <T : Any> T?.assertNotNull(name: String): T? {
	assert(this != null) {
		"Value $name is null"
	}
	return this
}
