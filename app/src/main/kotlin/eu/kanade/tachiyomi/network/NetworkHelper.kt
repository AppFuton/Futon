package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

/**
 * Mihon-compatible NetworkHelper interface.
 * Provides access to OkHttpClient for extensions.
 * 
 * This will be implemented by app to bridge with its existing network stack.
 */
abstract class NetworkHelper {
    
    /**
     * The default OkHttpClient with CloudFlare bypassing.
     */
    abstract val client: OkHttpClient
    
    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    open val cloudflareClient: OkHttpClient
        get() = client
    
    /**
     * Returns the default user agent string.
     */
    abstract fun defaultUserAgentProvider(): String
}
