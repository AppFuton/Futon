@file:Suppress("UnusedReceiverParameter")

package io.github.landwarderer.futon.core.util.ext

@Suppress("NOTHING_TO_INLINE")
inline fun Throwable.printStackTraceDebug() = Unit

fun assertNotInMainThread() = Unit
