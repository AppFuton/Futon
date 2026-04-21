package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * A stubbed CloudflareInterceptor for Mihon extension compatibility.
 * Modern Mihon handles Cloudflare via the main client, so this is mostly a passthrough.
 */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
