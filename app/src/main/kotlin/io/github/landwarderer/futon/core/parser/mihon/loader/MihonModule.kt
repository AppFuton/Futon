package io.github.landwarderer.futon.core.parser.mihon.loader

import android.app.Application
import android.content.Context
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge class to provide host app dependencies to Mihon extensions.
 * Mihon extensions expect these via Injekt, but since Futon uses Hilt,
 * we provide a way to access them.
 */
@Singleton
class MihonModule @Inject constructor(
	val application: Application,
	@MangaHttpClient val httpClient: OkHttpClient,
	val cookieJar: MutableCookieJar
) {
	val json = Json {
		ignoreUnknownKeys = true
		explicitNulls = false
	}

	/**
	 * Returns a [Context] compatible with Mihon extensions.
	 */
	fun getContext(): Context = application
}
