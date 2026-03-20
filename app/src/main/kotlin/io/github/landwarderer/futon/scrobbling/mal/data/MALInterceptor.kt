package io.github.landwarderer.futon.scrobbling.mal.data

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import io.github.landwarderer.futon.core.network.CommonHeaders
import org.koitharu.kotatsu.parsers.util.mimeType
import org.koitharu.kotatsu.parsers.util.parseHtml
import io.github.landwarderer.futon.scrobbling.common.data.ScrobblerStorage
import io.github.landwarderer.futon.scrobbling.common.domain.ScrobblerAuthRequiredException
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblerService
import java.net.HttpURLConnection

private const val JSON = "application/json"
private const val HTML = "text/html"

class MALInterceptor(private val storage: ScrobblerStorage) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val sourceRequest = chain.request()
		val request = sourceRequest.newBuilder()
		request.header(CommonHeaders.CONTENT_TYPE, JSON)
		request.header(CommonHeaders.ACCEPT, JSON)
		val isAuthRequest = sourceRequest.url.pathSegments.contains("oauth")
		if (!isAuthRequest) {
			storage.accessToken?.let {
				request.header(CommonHeaders.AUTHORIZATION, "Bearer $it")
			}
		}
		val response = chain.proceed(request.build())
		if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			throw ScrobblerAuthRequiredException(ScrobblerService.MAL)
		}
		if (response.mimeType == HTML) {
			throw IOException(response.parseHtml().title())
		}
		return response
	}

}
