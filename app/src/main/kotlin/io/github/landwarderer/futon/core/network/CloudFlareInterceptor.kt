package io.github.landwarderer.futon.core.network

import android.os.Build
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

class CloudFlareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Android 6 (SDK 23) has a bug in the implementation of the
        // "International Components for Unicode (ICU)" charset decoder used by "java.nio".
        // The error occurs when reading a stream into a String if the byte sequence
        // is interpreted in a way that causes the ICU decoder to flush its buffer incorrectly,
        // resulting in a negative position in the underlying "ByteBuffer".
        //
        // As a workaround, we need to manually decode the bytes to avoid the ICU "Bad position" bug
        // when running on Android 6.
        val protectionType = if (Build.VERSION.SDK_INT == 23) {
            try {
                // Peek up to 512 bytes safely.
                val bodyBytes = response.peekBody(512).bytes()
                val bodyString = String(bodyBytes, Charsets.UTF_8)

                when {
                    // Check for common Cloudflare challenge indicators
                    bodyString.contains("cf-challenge") ||
                        bodyString.contains("ray_id") ||
                        bodyString.contains("jschl_vc") -> {
                        CloudFlareHelper.PROTECTION_CAPTCHA
                    }
                    // Check for access denied/blocked
                    bodyString.contains("cf-error-details") -> {
                        CloudFlareHelper.PROTECTION_BLOCKED
                    }

                    else -> CloudFlareHelper.PROTECTION_NOT_DETECTED
                }
            } catch (e: Exception) {
                e.printStackTraceDebug("CloudFlareInterceptor")
                CloudFlareHelper.PROTECTION_NOT_DETECTED
            }
        } else {
            // Standard path for Android 7.0+
            try {
                CloudFlareHelper.checkResponseForProtection(response)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("Bad position") == true) {
                    CloudFlareHelper.PROTECTION_NOT_DETECTED
                } else {
                    e.printStackTraceDebug("CloudFlareInterceptor")
                }
            }
        }

        return when (protectionType) {
            CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                CloudFlareBlockedException(
                    url = request.url.toString(),
                    source = request.tag(MangaSource::class.java),
                ),
            )

            CloudFlareHelper.PROTECTION_CAPTCHA -> response.closeThrowing(
                CloudFlareProtectedException(
                    url = request.url.toString(),
                    source = request.tag(MangaSource::class.java),
                    headers = request.headers,
                ),
            )

            else -> response
        }
    }

    private fun Response.closeThrowing(error: IOException): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }
}
