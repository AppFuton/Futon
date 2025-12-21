package io.github.landwarderer.futon.core.util.ext

import io.github.landwarderer.futon.core.io.NullOutputStream
import java.io.ObjectOutputStream

@Suppress("UNCHECKED_CAST")
fun <T> Class<T>.castOrNull(obj: Any?): T? {
	if (obj == null || !isInstance(obj)) {
		return null
	}
	return obj as T
}

fun Any.isSerializable() = runCatching {
	val oos = ObjectOutputStream(NullOutputStream())
	oos.writeObject(this)
	oos.flush()
}.isSuccess
