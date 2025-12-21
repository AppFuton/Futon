package io.github.landwarderer.futon.sync.data

import dagger.Reusable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.github.landwarderer.futon.core.exceptions.SyncApiException
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.util.ext.toRequestBody
import io.github.landwarderer.futon.parsers.util.await
import io.github.landwarderer.futon.parsers.util.parseJson
import io.github.landwarderer.futon.parsers.util.parseRaw
import io.github.landwarderer.futon.parsers.util.removeSurrounding
import javax.inject.Inject

@Reusable
class SyncAuthApi @Inject constructor(
	@BaseHttpClient private val okHttpClient: OkHttpClient,
) {

	suspend fun authenticate(syncURL: String, email: String, password: String): String {
		val body = JSONObject(
			mapOf("email" to email, "password" to password),
		).toRequestBody()
		val request = Request.Builder()
			.url("$syncURL/auth")
			.post(body)
			.build()
		val response = okHttpClient.newCall(request).await()
		if (response.isSuccessful) {
			return response.parseJson().getString("token")
		} else {
			val code = response.code
			val message = response.parseRaw().removeSurrounding('"')
			throw SyncApiException(message, code)
		}
	}
}
