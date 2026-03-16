package io.github.landwarderer.futon.core.util.ext

import android.os.Looper
import android.util.Log

fun Throwable.printStackTraceDebug() = printStackTrace()

fun Throwable.printStackTraceDebug(tag: String) = Log.e(tag, this.stackTraceToString())

fun assertNotInMainThread() = check(Looper.myLooper() != Looper.getMainLooper()) {
	"Calling this from the main thread is prohibited"
}
