package io.github.landwarderer.futon.core.network.webview

/**
 * Represents a WebView request that was intercepted during page loading.
 * Contains the URL, HTTP method, headers, and timestamp of the request.
 */
data class InterceptedRequest(
    /**
     * The full URL of the intercepted request
     */
    val url: String,

    /**
     * HTTP method (GET, POST, etc.)
     */
    val method: String,

    /**
     * Request headers as key-value pairs
     */
    val headers: Map<String, String>,

    /**
     * Timestamp when the request was intercepted (System.currentTimeMillis())
     */
    val timestamp: Long,

    /**
     * Optional request body for POST requests
     */
    val body: String? = null,
) {
    /**
     * Extract parameter value from URL query string
     */
    fun getQueryParameter(name: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null

        return query.split('&')
            .map { it.split('=', limit = 2) }
            .find { it.size == 2 && it[0] == name }
            ?.get(1)
    }

    /**
     * Check if URL matches a pattern
     */
    fun urlMatches(pattern: Regex): Boolean = pattern.containsMatchIn(url)

    /**
     * Check if URL contains a specific substring
     */
    fun urlContains(substring: String): Boolean = url.contains(substring, ignoreCase = true)
}

/**
 * Callback interface for WebView request interception
 */
interface WebViewRequestInterceptor {
    /**
     * Called when a request is intercepted.
     * Return true to capture this request, false to ignore it.
     */
    fun shouldCaptureRequest(request: InterceptedRequest): Boolean

    /**
     * Called when interception is complete (timeout or manual stop)
     */
    fun onInterceptionComplete(capturedRequests: List<InterceptedRequest>)

    /**
     * Called if interception fails due to error
     */
    fun onInterceptionError(error: Throwable)
}

/**
 * Configuration for WebView request interception
 */
// kotlin
data class InterceptionConfig(
    val timeoutMs: Long,
    val maxRequests: Int = 100,
    val urlPattern: Regex? = null,
    val filterScript: String? = null,   // JS containing predicate (last return)
    val pageScript: String? = null      // JS to actually run in the page
)
