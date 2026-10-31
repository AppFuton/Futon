package io.github.landwarderer.futon.core.network.webview

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.WorkerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.landwarderer.futon.browser.BrowserCallback
import io.github.landwarderer.futon.browser.BrowserClient
import io.github.landwarderer.futon.core.network.webview.adblock.AdBlock
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebViewClient that intercepts and captures HTTP requests for VRF token extraction
 * and other dynamic data extraction from AJAX requests.
 */
class RequestInterceptorWebViewClient(
    callback: BrowserCallback,
    adBlock: AdBlock?,
    private val config: InterceptionConfig,
    private val interceptor: WebViewRequestInterceptor,
) : BrowserClient(callback, adBlock) {

    private val capturedRequests = Collections.synchronizedList(mutableListOf<InterceptedRequest>())
    private val mutex = Mutex()
    private val isCapturing = AtomicBoolean(true)
    private val startTime = System.currentTimeMillis()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pollScheduled = AtomicBoolean(false)

    @WorkerThread
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        // Always call parent first for ad blocking and other functionality
        val parentResponse = super.shouldInterceptRequest(view, request)

        // Capture request if still within timeout and capturing
        if (isCapturing.get() && request != null && !isTimeoutReached()) {
            captureRequestIfMatches(request)
        }

        return parentResponse
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Capture navigation events (like window.location.href = "...")
        if (isCapturing.get() && request != null && !isTimeoutReached()) {
            if (captureRequestIfMatches(request)) {
                return true // Stop the WebView from loading the intercepted URL
            }
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (view != null) {
            injectPageScript(view)
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectPageScript(view)
        schedulePolling(view)
    }

    /**
     * The script has to be injected again on every navigation, not just the first one:
     * an interstitial (a Cloudflare challenge, a redirect) is its own document, and the page
     * that replaces it gets a fresh JS context. Scripts are expected to be idempotent.
     */
    private fun injectPageScript(view: WebView) {
        val script = config.pageScript
        if (script.isNullOrBlank() || !isCapturing.get() || isTimeoutReached()) {
            return
        }
        view.evaluateJavascript(script, null)
    }

    /**
     * Keeps re-running the script while a page is up, so a payload that only becomes
     * available after some in-page work, or after a challenge clears without a navigation,
     * is still picked up.
     */
    private fun schedulePolling(view: WebView) {
        if (config.pageScript.isNullOrBlank() || !pollScheduled.compareAndSet(false, true)) {
            return
        }
        val viewRef = WeakReference(view)
        mainHandler.postDelayed(
            object : Runnable {
                override fun run() {
                    val webView = viewRef.get()
                    if (webView == null || !isCapturing.get() || isTimeoutReached()) {
                        pollScheduled.set(false)
                        return
                    }
                    injectPageScript(webView)
                    mainHandler.postDelayed(this, SCRIPT_POLL_INTERVAL_MS)
                }
            },
            SCRIPT_POLL_INTERVAL_MS,
        )
    }

    /**
     * @return true if the request was captured
     */
    private fun captureRequestIfMatches(request: WebResourceRequest): Boolean {
        try {
            val interceptedRequest = InterceptedRequest(
                url = request.url.toString(),
                method = request.method,
                headers = request.requestHeaders,
                timestamp = System.currentTimeMillis()
            )

            // Check if request matches filtering criteria
            val shouldCapture = when {
                capturedRequests.size >= config.maxRequests -> false
                config.urlPattern != null && !interceptedRequest.urlMatches(config.urlPattern) -> false
                else -> interceptor.shouldCaptureRequest(interceptedRequest)
            }

            if (shouldCapture) {
                val shouldComplete = synchronized(capturedRequests) {
                    capturedRequests.add(interceptedRequest)
                    capturedRequests.size >= config.maxRequests
                }

                // If we've reached maxRequests, stop capturing immediately
                if (shouldComplete) {
                    Log.d(TAG_VRF, "Reached maxRequests (${config.maxRequests}), stopping capture immediately")
                    stopCapturing()
                }
                return true
            }
        } catch (e: Exception) {
            // Don't let interception errors break the WebView
            interceptor.onInterceptionError(e)
        }
        return false
    }

    private fun isTimeoutReached(): Boolean {
        return System.currentTimeMillis() - startTime > config.timeoutMs
    }

    private fun completeInterception() {
        try {
            val finalRequests = synchronized(capturedRequests) {
                capturedRequests.toList()
            }
            interceptor.onInterceptionComplete(finalRequests)
        } catch (e: Exception) {
            interceptor.onInterceptionError(e)
        }
    }

    /**
     * Manually stop capturing requests before timeout
     */
    fun stopCapturing() {
        if (isCapturing.compareAndSet(true, false)) {
            completeInterception()
        }
    }

    /**
     * Get currently captured requests (thread-safe)
     */
    fun getCapturedRequests(): List<InterceptedRequest> {
        return synchronized(capturedRequests) {
            capturedRequests.toList()
        }
    }

    private companion object {

        const val SCRIPT_POLL_INTERVAL_MS = 250L
    }
}
