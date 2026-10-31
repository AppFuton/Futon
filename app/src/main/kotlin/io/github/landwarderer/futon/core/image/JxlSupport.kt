package io.github.landwarderer.futon.core.image

import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug

/**
 * Whether JPEG XL can be decoded on this device.
 *
 * jxl-coder ships libraries for armeabi-v7a, arm64-v8a, x86 and x86_64. Should loading still fail - an
 * unexpected ABI, a translation layer missing something, a stripped install - this keeps it to one image
 * failing. It has to be asked before the first `JxlCoder` call: its class initializer loads the library,
 * and a failure there is an Error that would take the image pipeline down rather than fail one image.
 *
 * Only asked once an image has been recognised as JPEG XL, so nobody else pays for loading it.
 */
object JxlSupport {

	val isAvailable: Boolean by lazy {
		try {
			System.loadLibrary("jxlcoder")
			true
		} catch (e: LinkageError) {
			e.printStackTraceDebug()
			false
		}
	}
}
