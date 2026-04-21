package io.github.landwarderer.futon.mihon.parsers.network

import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAVAILABLE

public object CloudFlareHelper {

    public const val PROTECTION_NOT_DETECTED: Int = 0
    public const val PROTECTION_CAPTCHA: Int = 1
    public const val PROTECTION_BLOCKED: Int = 2

    private const val CF_CLEARANCE = "cf_clearance"

    public fun checkResponseForProtection(response: Response): Int {
        if (response.code != HTTP_FORBIDDEN && response.code != HTTP_UNAVAILABLE) {
            return PROTECTION_NOT_DETECTED
        }
        
        // Check headers for CloudFlare indicators first
        val cfRay = response.header("cf-ray")
        val server = response.header("server")
        val cfMitigated = response.header("cf-mitigated")
        val isCloudFlareServer = cfRay != null || server?.contains("cloudflare", ignoreCase = true) == true
        
        // If no CloudFlare headers, it's likely not CloudFlare protection
        if (!isCloudFlareServer) {
            return PROTECTION_NOT_DETECTED
        }
        
        // If cf-mitigated header is present with "challenge", it's definitely a CloudFlare challenge
        if (cfMitigated?.contains("challenge", ignoreCase = true) == true) {
            return PROTECTION_CAPTCHA
        }
        
        val content = try {
            response.peekBody(Long.MAX_VALUE).use {
                Jsoup.parse(it.byteStream(), Charsets.UTF_8.name(), response.request.url.toString())
            }
        } catch (_: IllegalStateException) {
            return PROTECTION_NOT_DETECTED
        }
        return when {
            content.selectFirst("h2[data-translate=\"blocked_why_headline\"]") != null -> PROTECTION_BLOCKED
            // CloudFlare "Just a moment" challenge page
            content.title().contains("Just a moment", ignoreCase = true) -> PROTECTION_CAPTCHA
            // More specific CloudFlare challenge detection
            (content.getElementById("challenge-error-title") != null || 
             content.getElementById("challenge-error-text") != null) &&
            (content.selectFirst("script[src*=\"/cdn-cgi/\"]") != null ||
             content.html().contains("cf-browser-verification") ||
             content.html().contains("__cf_chl_opt")) -> PROTECTION_CAPTCHA

            else -> PROTECTION_NOT_DETECTED
        }
    }



    public fun getClearanceCookie(cookieJar: CookieJar, url: String): String? {
        return cookieJar.loadForRequest(url.toHttpUrl()).find { it.name == CF_CLEARANCE }?.value
    }

    public fun isCloudFlareCookie(name: String): Boolean {
        return name.startsWith("cf_")
            || name.startsWith("_cf")
            || name.startsWith("__cf")
            || name == "csrftoken"
    }
}
