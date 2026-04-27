package io.github.landwarderer.futon.mihon.parsers

import io.github.landwarderer.futon.mihon.parsers.bitmap.Bitmap
import io.github.landwarderer.futon.mihon.parsers.config.ContentSourceConfig
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.network.UserAgents
import io.github.landwarderer.futon.mihon.parsers.util.LinkResolver
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.*

abstract class ContentLoaderContext {

	abstract val httpClient: OkHttpClient

	abstract val cookieJar: CookieJar

	abstract fun newParserInstance(source: ContentSource): ContentParser

	abstract fun newLinkResolver(link: HttpUrl): LinkResolver

	fun newLinkResolver(link: String): LinkResolver = newLinkResolver(link.toHttpUrl())

	open fun encodeBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

	open fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)

	open fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

	/**
	 * Optional user-facing notification, default no-op for non-Android environments.
	 */
	open fun showToast(message: String, isLong: Boolean = false) {}

	/**
	 * Execute JavaScript code and return result
	 * @param script JavaScript source code
	 * @return execution result as string, may be null
	 */
	@Deprecated("Provide a base url")
	abstract suspend fun evaluateJs(script: String): String?

	/**
	 * Execute JavaScript code and return result
	 * @param script JavaScript source code
	 * @param baseUrl url of page script will be executed in context of
	 * @return execution result as string, may be null
	 */
	abstract suspend fun evaluateJs(baseUrl: String, script: String): String?

	/**
	 * Open [url] in browser for some external action (e.g. captcha solving or non cookie-based authorization)
	 */
	open fun requestBrowserAction(parser: ContentParser, url: String): Nothing {
		throw UnsupportedOperationException("Browser is not available")
	}

	abstract fun getConfig(source: ContentSource): ContentSourceConfig

	open fun getDefaultUserAgent(): String = UserAgents.CHROME_MOBILE

	/**
	 * Helper function to be used in an interceptor
	 * to descramble images
	 * @param response Image response
	 * @param redraw lambda function to implement descrambling logic
	 */
	abstract fun redrawImageResponse(
		response: Response,
		redraw: (image: Bitmap) -> Bitmap,
	): Response

	/**
	 * create a new empty Bitmap with given dimensions
	 */
	abstract fun createBitmap(
		width: Int,
		height: Int,
	): Bitmap
}
