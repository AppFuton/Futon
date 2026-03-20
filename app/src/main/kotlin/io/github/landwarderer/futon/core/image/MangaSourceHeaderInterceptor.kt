package io.github.landwarderer.futon.core.image

import coil3.intercept.Interceptor
import coil3.network.httpHeaders
import coil3.request.ImageResult
import io.github.landwarderer.futon.core.model.unwrap
import io.github.landwarderer.futon.core.network.CommonHeaders
import io.github.landwarderer.futon.core.util.ext.mangaSourceKey
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class MangaSourceHeaderInterceptor : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val mangaSource = chain.request.extras[mangaSourceKey]?.unwrap() as? MangaParserSource ?: return chain.proceed()
		val request = chain.request
		val newHeaders = request.httpHeaders.newBuilder()
			.set(CommonHeaders.MANGA_SOURCE, mangaSource.name)
			.build()
		val newRequest = request.newBuilder()
			.httpHeaders(newHeaders)
			.build()
		return chain.withRequest(newRequest).proceed()
	}
}
