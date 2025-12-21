package io.github.landwarderer.futon.core.network

import okhttp3.Interceptor
import okhttp3.Response

class CurlLoggingInterceptor : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		return chain.proceed(chain.request()) // no-op implementation
	}
}
