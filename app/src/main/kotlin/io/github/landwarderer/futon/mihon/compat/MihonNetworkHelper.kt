package io.github.landwarderer.futon.mihon.compat

import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import io.github.landwarderer.futon.mihon.model.toMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.network.CloudFlareHelper
import io.github.landwarderer.futon.mihon.parsers.network.UserAgents
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Implementation of Mihon's NetworkHelper interface.
 * 
 * Wraps App's existing OkHttpClient to provide Mihon extensions with
 * access to the network stack, including CloudFlare bypassing and cookie management.
 * 
 * Note: We create a new client without GZipInterceptor because Mihon extensions
 * handle their own request encoding. App's GZipInterceptor incorrectly
 * adds Content-Encoding: gzip header without actually compressing the body,
 * which causes server-side decompression errors (e.g., Picacomic login fails with
 * "incorrect header check").
 */
class MihonNetworkHelper(
    baseClient: OkHttpClient,
    val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
) : NetworkHelper() {
    
    /**
     * The OkHttpClient for Mihon extensions.
     * We rebuild without GZipInterceptor to prevent incorrect Content-Encoding headers.
     */
    override val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
        
        // Copy configuration from base client
        builder.connectTimeout(baseClient.connectTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(baseClient.readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        builder.writeTimeout(baseClient.writeTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        builder.cookieJar(baseClient.cookieJar)
        builder.dns(baseClient.dns)
        builder.cache(baseClient.cache)
        builder.dispatcher(baseClient.dispatcher)
        builder.connectionPool(baseClient.connectionPool)
        builder.followRedirects(baseClient.followRedirects)
        builder.followSslRedirects(baseClient.followSslRedirects)
        builder.retryOnConnectionFailure(baseClient.retryOnConnectionFailure)
        
        // Wrap exceptions thrown by subsequent interceptors (especially from extensions)
        builder.addInterceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: Throwable) {
                // OkHttp Dispatcher will crash the app if intercepted throws unchecked exception instead of IOException.
                // Extensions (like Baozi) might throw plain Exceptions for errors like "Socket closed".
                if (e is IOException) throw e
                throw IOException(e.message, e)
            }
        }
        
        // Copy interceptors but exclude GZipInterceptor
        baseClient.interceptors.forEach { interceptor ->
            if (interceptor.javaClass.simpleName != "GZipInterceptor") {
                builder.addInterceptor(interceptor)
            } else {
                Log.d("MihonNetworkHelper", "Skipping GZipInterceptor for Mihon client")
            }
        }
        
        // Copy network interceptors
        baseClient.networkInterceptors.forEach { interceptor ->
            builder.addNetworkInterceptor(interceptor)
        }

        // Add a Mihon-specific fallback detector.
        // Some Mihon sources build their own clients from network.cloudflareClient, and in practice
        // the copied base interceptor chain is not always enough to surface App's CF flow.
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val request = enrichApiRequestHeadersIfNeeded(originalRequest)
            val response = chain.proceed(request)
            val challengeUrl = request.toChallengeUrl()
            when (CloudFlareHelper.checkResponseForProtection(response)) {
                CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                    CloudFlareBlockedException(
                        url = challengeUrl,
                        source = request.tag(ContentSource::class.java) as MangaSource?,
                    ),
                )

                CloudFlareHelper.PROTECTION_CAPTCHA -> {
                    val host = request.url.host.lowercase()
                    val clearance = cookieJar.loadForRequest(request.url)
                        .firstOrNull { it.name == "cf_clearance" }
                        ?.value

                    tryFetchWithWebView(request)?.let { browserResponse ->
                        val browserProtection = CloudFlareHelper.checkResponseForProtection(browserResponse)
                        if (browserProtection == CloudFlareHelper.PROTECTION_NOT_DETECTED) {
                            Log.i(
                                "MihonNetwork",
                                "WebView fallback succeeded for host=$host, status=${browserResponse.code}",
                            )
                            response.close()
                            return@addInterceptor browserResponse
                        }
                        Log.w(
                            "MihonNetwork",
                            "WebView fallback still protected for host=$host, status=${browserResponse.code}",
                        )
                        browserResponse.close()
                    }

                    if (shouldSkipInteractiveAction(host, clearance)) {
                        Log.w(
                            "MihonNetwork",
                            "Skip interactive action for host=$host: repeated challenge with same cf_clearance",
                        )
                        response.closeThrowing(
                            CloudFlareBlockedException(
                                url = challengeUrl,
                                source = request.tag(ContentSource::class.java),
                            ),
                        )
                    } else {
                        val source = request.tag(ContentSource::class.java)
                        if (source == null) {
                            Log.w("MihonNetwork", "Missing ContentSource tag for host=$host")
                            response.closeThrowing(CloudFlareBlockedException(url = challengeUrl, source = null))
                        } else {
                            response.closeThrowing(
                                InteractiveActionRequiredException(
                                    source = source.toMangaSource(),
                                    url = challengeUrl,
                                ),
                            )
                        }
                    }
                }

                else -> response
            }
        }
        
        // Add debug logging interceptor for Mihon extensions
        builder.addInterceptor { chain ->
            val request = chain.request()
            val requestCookies = cookieJar.loadForRequest(request.url)
            val cfClearanceCookie = requestCookies.firstOrNull { it.name == "cf_clearance" }?.value
            val cookieNames = requestCookies.joinToString(",") { it.name }
            Log.d(
                "MihonNetwork",
                "RequestMeta: host=${request.url.host}, ua=${request.header("User-Agent")}, referer=${request.header("Referer")}, origin=${request.header("Origin")}, hasCfClearance=${cfClearanceCookie != null}, cfClearance=${maskCookieValue(cfClearanceCookie)}, cookies=[$cookieNames]",
            )
            Log.d("MihonNetwork", "Request: ${request.method} ${request.url}")
            
            val response = chain.proceed(request)
            
            // Log response info
            val responseCode = response.code
            val contentType = response.header("Content-Type")
            Log.d(
                "MihonNetwork",
                "Response: $responseCode, Content-Type: $contentType, cf-ray=${response.header("cf-ray")}, cf-mitigated=${response.header("cf-mitigated")}, server=${response.header("server")}, URL: ${request.url}",
            )
            
            // If response is not successful, log the first 200 chars of body for debugging
            if (!response.isSuccessful) {
                val source = response.body.source()
                source.request(200)
                val buffer = source.buffer.clone()
                val preview = buffer.readUtf8(minOf(200, buffer.size))
                Log.w("MihonNetwork", "Non-successful response ($responseCode) preview: $preview")
            }
            
            response
        }
        
        builder.build()
    }
    
    /**
     * @deprecated Since extension-lib 1.5, CloudFlare is handled by the regular client.
     */
    @Deprecated("The regular client handles Cloudflare by default")
    override val cloudflareClient: OkHttpClient = client
    
    /**
     * Returns the default user agent string.
     */
    override fun defaultUserAgentProvider(): String = UserAgents.CHROME_MOBILE

    private fun Response.closeThrowing(error: Throwable): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }

    private fun Request.toChallengeUrl(): String {
        val referer = header("Referer")?.toHttpUrlOrNull()
        if (referer != null && referer.host == url.host) {
            return referer.newBuilder()
                .query(null)
                .fragment(null)
                .build()
                .toString()
        }
        return url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    private fun enrichApiRequestHeadersIfNeeded(request: Request): Request {
        if (!request.url.encodedPath.startsWith("/api/")) return request
        val cookies = cookieJar.loadForRequest(request.url)
        val hasCfClearance = cookies.any { it.name == "cf_clearance" }
        if (!hasCfClearance) return request
        val origin = "${request.url.scheme}://${request.url.host}"
        var modified = false
        val builder = request.newBuilder()
        if (request.header("Referer").isNullOrBlank()) {
            builder.header("Referer", "$origin/")
            modified = true
        }
        if (request.header("Origin").isNullOrBlank()) {
            builder.header("Origin", origin)
            modified = true
        }
        if (request.header("Accept").isNullOrBlank()) {
            builder.header("Accept", "application/json, text/plain, */*")
            modified = true
        }
        if (request.header("Accept-Language").isNullOrBlank()) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
            modified = true
        }
        if (request.header("Sec-Fetch-Site").isNullOrBlank()) {
            builder.header("Sec-Fetch-Site", "same-origin")
            modified = true
        }
        if (request.header("Sec-Fetch-Mode").isNullOrBlank()) {
            builder.header("Sec-Fetch-Mode", "cors")
            modified = true
        }
        if (request.header("Sec-Fetch-Dest").isNullOrBlank()) {
            builder.header("Sec-Fetch-Dest", "empty")
            modified = true
        }
        if (request.header("X-Requested-With").isNullOrBlank()) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            modified = true
        }
        if (request.header("X-XSRF-TOKEN").isNullOrBlank()) {
            val xsrf = cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value
            val decodedXsrf = xsrf?.let {
                runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrDefault(it)
            }
            if (!decodedXsrf.isNullOrBlank()) {
                builder.header("X-XSRF-TOKEN", decodedXsrf)
                modified = true
            }
        }
        return if (modified) builder.build() else request
    }

    private fun maskCookieValue(value: String?): String {
        if (value.isNullOrEmpty()) return "<empty>"
        return if (value.length <= 8) "***" else "${value.take(4)}...${value.takeLast(4)}"
    }

    private fun tryFetchWithWebView(request: Request): Response? {
        if (request.method != "GET") {
            Log.d("MihonNetwork", "WebView fallback skipped: non-GET ${request.method}")
            return null
        }
        val executor = webViewExecutor
        if (executor == null) {
            Log.w("MihonNetwork", "WebView fallback skipped: WebViewExecutor is null")
            return null
        }
        val cookies = cookieJar.loadForRequest(request.url)
        val hasCfClearance = cookies.any { it.name == "cf_clearance" }
        if (!hasCfClearance) {
            Log.d("MihonNetwork", "WebView fallback skipped: no cf_clearance for host=${request.url.host}")
            return null
        }
        
        Log.i("MihonNetwork", "WebView fallback is disabled due to missing implementation for WebViewExecutor.fetchWithBrowserContext")
        return null
    }

    private fun shouldSkipInteractiveAction(host: String, clearance: String?): Boolean {
        if (clearance.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val last = recentChallengeAttempts[host]
        if (last == null || now - last.timestampMs > INTERACTIVE_RETRY_WINDOW_MS || last.clearance != clearance) {
            recentChallengeAttempts[host] = ChallengeAttempt(
                clearance = clearance,
                timestampMs = now,
                count = 1,
            )
            return false
        }
        val nextCount = last.count + 1
        recentChallengeAttempts[host] = last.copy(
            timestampMs = now,
            count = nextCount,
        )
        return nextCount >= 2
    }

    private data class ChallengeAttempt(
        val clearance: String,
        val timestampMs: Long,
        val count: Int,
    )

    companion object {
        private const val INTERACTIVE_RETRY_WINDOW_MS = 10 * 60 * 1000L
        private val recentChallengeAttempts = ConcurrentHashMap<String, ChallengeAttempt>()
    }
}
