package io.github.landwarderer.futon.novel.data.lnreader.runtime

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.network.CloudFlareInterceptor
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.lnreader.model.LnChapterItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnNovelItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.model.LnSourceNovel
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import javax.inject.Inject

/**
 * Manages an isolated QuickJS execution context to run an LNReader JavaScript plugin.
 *
 * Trust Model:
 * LNReader plugins run inside an isolated QuickJS JavaScript engine without filesystem or
 * direct OS access. Network calls from plugins are bridged via the application's OkHttpClient
 * (preserving cookies and redirection headers required for Cloudflare-protected novel sites).
 * In alignment with Mihon/Tachiyomi extensions, LNReader community plugins are considered
 * user-trusted external sources.
 */
class LnPluginRuntime(
	private val context: Context,
	httpClient: OkHttpClient,
	private val pluginId: String,
	private val pluginScript: String,
	private val pluginSite: String = "",
) : Closeable {
	// LNReader plugins need the raw HTTP response so their own fetch/error handling can
	// inspect challenge pages. Futon's reader interceptor throws and loses that response.
	private val httpClient = httpClient.newBuilder().apply {
		interceptors().removeAll { it is CloudFlareInterceptor }
	}.build()

	constructor(
		context: Context,
		httpClient: OkHttpClient,
		plugin: LnPlugin,
		pluginStorage: LnPluginStorage,
	) : this(
		context = context,
		httpClient = httpClient,
		pluginId = plugin.id,
		pluginScript = pluginStorage.getPluginScript(plugin.id).let { file ->
			if (file.exists()) file.readText(Charsets.UTF_8) else ""
		},
		pluginSite = plugin.site,
	)

	private var quickJs: QuickJs? = null
	@Volatile private var isClosed = false
	private val mutex = Mutex()
	private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val prefs by lazy {
		context.getSharedPreferences("ln_plugin_$pluginId", Context.MODE_PRIVATE)
	}

	companion object {
		private const val PLUGIN_TIMEOUT_MS = 30_000L

		private fun JSONObject.optStringOrNull(name: String): String? {
			if (isNull(name)) return null
			val value = optString(name, "")
			return value.takeIf { it.isNotBlank() && it != "null" }
		}
	}

	private suspend fun ensureInitialized() {
		if (isClosed) {
			throw LnPluginRuntimeClosedException(pluginId)
		}
		if (quickJs != null) return
		check(pluginScript.isNotBlank()) {
			"LNReader plugin '$pluginId' is not installed or has no script"
		}

		val qjs = QuickJs.create(jobDispatcher = Dispatchers.IO)
		try {
			// Bind native logging bridge
			qjs.function("__futon_native_log") { args ->
				val level = args.getOrNull(0)?.toString().orEmpty()
				val msg = args.drop(1).joinToString(" ")
				when (level) {
					"ERROR" -> android.util.Log.e("LnPluginRuntime", "[$pluginId] $msg")
					"WARN" -> android.util.Log.w("LnPluginRuntime", "[$pluginId] $msg")
					else -> android.util.Log.d("LnPluginRuntime", "[$pluginId] $msg")
				}
			}

			// Bind native HTTP fetch bridge
			qjs.asyncFunction("__futon_native_fetch") { args ->
				val url = args.getOrNull(0)?.toString().orEmpty()
				val method = args.getOrNull(1)?.toString() ?: "GET"
				val headersJson = args.getOrNull(2)?.toString().orEmpty()
				val body = args.getOrNull(3)?.toString()

				executeNativeFetch(url, method, headersJson, body)
			}

			// Bind native storage bridges
			qjs.function("__futon_native_storage_get") { args ->
				val key = args.getOrNull(0)?.toString().orEmpty()
				prefs.getString(key, null)
			}

			qjs.function("__futon_native_storage_set") { args ->
				val key = args.getOrNull(0)?.toString().orEmpty()
				val value = args.getOrNull(1)?.toString().orEmpty()
				prefs.edit().putString(key, value).apply()
			}

			// 1. Load bundled Cheerio, Parser, Dayjs dependencies
			val depsCode = context.assets.open("lnreader/ln_deps.min.js").bufferedReader().use { it.readText() }
			qjs.evaluate<Any?>(depsCode)

			// 2. Load LNReader environment shim
			val envCode = context.assets.open("lnreader/ln_env.js").bufferedReader().use { it.readText() }
			qjs.evaluate<Any?>(envCode)

			// 3. Initialize the plugin code
			qjs.evaluate<Any?>("__futon_raw_code = " + JSONObject.quote(pluginScript) + ";")
			val initSuccess = qjs.evaluate<Boolean>("__futon_init_plugin(__futon_raw_code);")
			if (!initSuccess) {
				throw IllegalStateException("Failed to initialize LNReader plugin: $pluginId")
			}

			quickJs = qjs
		} catch (e: Throwable) {
			qjs.close()
			throw e
		}
	}

	suspend fun getPopularNovels(page: Int): List<LnNovelItem> = mutex.withLock {
		withContext(Dispatchers.IO) {
			ensureInitialized()
			val qjs = checkNotNull(quickJs)
			val script = """
				(async () => {
					const novels = await __futon_current_plugin.popularNovels($page, { showLatestNovels: false });
					return JSON.stringify(novels || []);
				})()
			""".trimIndent()

			val jsonResult = evaluateAsyncString(qjs, script)
			parseNovelItems(jsonResult)
		}
	}

	suspend fun searchNovels(query: String, page: Int): List<LnNovelItem> = mutex.withLock {
		withContext(Dispatchers.IO) {
			ensureInitialized()
			val qjs = checkNotNull(quickJs)
			val quotedQuery = JSONObject.quote(query)
			val script = """
				(async () => {
					const novels = await __futon_current_plugin.searchNovels($quotedQuery, $page);
					return JSON.stringify(novels || []);
				})()
			""".trimIndent()

			val jsonResult = evaluateAsyncString(qjs, script)
			parseNovelItems(jsonResult)
		}
	}

	suspend fun parseNovel(novelPath: String): LnSourceNovel = mutex.withLock {
		withContext(Dispatchers.IO) {
			ensureInitialized()
			val qjs = checkNotNull(quickJs)
			val quotedPath = JSONObject.quote(novelPath)
			val script = """
				(async () => {
					const novel = await __futon_current_plugin.parseNovel($quotedPath);
					return JSON.stringify(novel || {});
				})()
			""".trimIndent()

			val jsonResult = evaluateAsyncString(qjs, script)
			parseSourceNovel(jsonResult)
		}
	}

	suspend fun parseChapter(chapterPath: String): String = mutex.withLock {
		withContext(Dispatchers.IO) {
			ensureInitialized()
			val qjs = checkNotNull(quickJs)
			val quotedPath = JSONObject.quote(chapterPath)
			val script = """
				(async () => {
					const content = await __futon_current_plugin.parseChapter($quotedPath);
					return content || "";
				})()
			""".trimIndent()

			evaluateAsyncString(qjs, script)
		}
	}

	/** Evaluate a Promise-returning plugin expression and read its fulfilled value. */
	private suspend fun evaluateAsyncString(qjs: QuickJs, promiseExpression: String): String {
		val wrapper = """
			(async () => {
				try {
					const res = await ($promiseExpression);
					return JSON.stringify({ success: true, data: (res == null ? "" : String(res)) });
				} catch (err) {
					const msg = (err && (err.message ? (err.message + '\n' + (err.stack || '')) : err.stack)) || String(err);
					return JSON.stringify({ success: false, error: msg });
				}
			})()
		""".trimIndent()

		val rawJson = withTimeoutOrNull(PLUGIN_TIMEOUT_MS) {
			qjs.evaluate<String>(wrapper)
		} ?: throw IllegalStateException("Plugin evaluation timed out after ${PLUGIN_TIMEOUT_MS / 1000}s")

		if (rawJson.isNullOrBlank()) {
			throw IllegalStateException("Plugin evaluation completed without result or error")
		}

		val json = try {
			JSONObject(rawJson)
		} catch (e: Throwable) {
			throw IllegalStateException("Plugin evaluation returned invalid response wrapper: $rawJson", e)
		}

		if (!json.optBoolean("success", false)) {
			val error = json.optString("error", "Unknown plugin evaluation error")
			throw IllegalStateException(error)
		}
		return json.optString("data", "")
	}

	private suspend fun executeNativeFetch(
		url: String,
		method: String,
		headersJson: String,
		body: String?,
	): String = withContext(Dispatchers.IO) {
		try {
			if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
				return@withContext JSONObject().apply {
					put("status", 400)
					put("statusText", "Bad Request")
					put("body", "")
					put("error", "Unsupported URL scheme: only HTTP and HTTPS are allowed")
				}.toString()
			}

			val requestUrl = runCatching {
				val requested = java.net.URI(url)
				if (requested.isAbsolute) requested.toString() else java.net.URI(pluginSite).resolve(requested).toString()
			}.getOrDefault(url)
			val requestBuilder = Request.Builder().url(requestUrl)
			val headersObj = if (headersJson.isNotBlank()) JSONObject(headersJson) else JSONObject()
			val keys = headersObj.keys()
			while (keys.hasNext()) {
				val key = keys.next()
				requestBuilder.header(key, headersObj.getString(key))
			}

			val normalizedMethod = method.uppercase()
			val contentTypeHeader = headersObj.keys().asSequence()
				.firstOrNull { it.equals("Content-Type", ignoreCase = true) }
			val contentType = contentTypeHeader?.let(headersObj::optString)
				?.takeIf(String::isNotBlank)
				?.toMediaTypeOrNull()
			val methodRequiresBody = normalizedMethod in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")

			val requestBody = if (normalizedMethod !in setOf("GET", "HEAD") && (body != null || methodRequiresBody)) {
				if (body != null && body.startsWith("{\"__futon_type\":\"FormData\"")) {
					// OkHttp's MultipartBody supplies its own boundary. Keep a plugin-provided
					// bare multipart Content-Type from suppressing that generated boundary.
					requestBuilder.removeHeader("Content-Type")
					val json = JSONObject(body)
					val entries = json.optJSONArray("entries") ?: JSONArray()
					val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
					for (i in 0 until entries.length()) {
						val entry = entries.getJSONObject(i)
						val name = entry.getString("name")
						val value = entry.getString("value")
						val filename = if (!entry.isNull("filename")) entry.optString("filename") else null
						if (filename != null) {
							formBuilder.addFormDataPart(
								name,
								filename,
								value.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
							)
						} else {
							formBuilder.addFormDataPart(name, value)
						}
					}
					formBuilder.build()
				} else {
					(body ?: "").toRequestBody(contentType)
				}
			} else null
			requestBuilder.method(normalizedMethod, requestBody)

			httpClient.newCall(requestBuilder.build()).execute().use { response ->
				val bodyString = response.body?.string().orEmpty()
				val headersMap = JSONObject()
				for (headerName in response.headers.names()) {
					headersMap.put(headerName.lowercase(), response.header(headerName))
				}

				JSONObject().apply {
					put("url", response.request.url.toString())
					put("status", response.code)
					put("statusText", response.message)
					put("body", bodyString)
					put("headers", headersMap)
				}.toString()
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug("LnPluginRuntime[$pluginId]")
			JSONObject().apply {
				put("status", 0)
				put("statusText", "Network Error")
				put("error", e.message ?: e.javaClass.simpleName)
				put("headers", JSONObject())
			}.toString()
		}
	}

	private fun parseNovelItems(jsonString: String): List<LnNovelItem> {
		if (jsonString.isBlank()) {
			throw IllegalStateException("Plugin returned empty list result")
		}
		val array = try {
			JSONArray(jsonString)
		} catch (e: Throwable) {
			throw IllegalStateException("Plugin returned malformed JSON: ${e.message}", e)
		}
		val list = ArrayList<LnNovelItem>(array.length())
		for (i in 0 until array.length()) {
			val obj = array.getJSONObject(i)
			list.add(
				LnNovelItem(
					name = obj.optStringOrNull("name") ?: "Untitled",
					path = obj.optStringOrNull("path").orEmpty(),
					cover = obj.optStringOrNull("cover"),
				),
			)
		}
		return list
	}

	private fun parseSourceNovel(jsonString: String): LnSourceNovel {
		if (jsonString.isBlank()) {
			throw IllegalStateException("Plugin returned empty novel details")
		}
		val obj = try {
			JSONObject(jsonString)
		} catch (e: Throwable) {
			throw IllegalStateException("Plugin returned malformed JSON: ${e.message}", e)
		}
		val chaptersArray = obj.optJSONArray("chapters") ?: JSONArray()
		val chapters = ArrayList<LnChapterItem>(chaptersArray.length())
		for (i in 0 until chaptersArray.length()) {
			val cObj = chaptersArray.getJSONObject(i)
			val chapNum = if (cObj.has("chapterNumber") && !cObj.isNull("chapterNumber")) {
				cObj.optDouble("chapterNumber", 0.0).toFloat()
			} else null

			chapters.add(
				LnChapterItem(
					name = cObj.optStringOrNull("name") ?: "Chapter ${i + 1}",
					path = cObj.optStringOrNull("path").orEmpty(),
					chapterNumber = chapNum,
					releaseTime = cObj.optStringOrNull("releaseTime"),
				),
			)
		}

		return LnSourceNovel(
			name = obj.optStringOrNull("name") ?: "Untitled",
			path = obj.optStringOrNull("path").orEmpty(),
			cover = obj.optStringOrNull("cover"),
			author = obj.optStringOrNull("author"),
			artist = obj.optStringOrNull("artist"),
			status = obj.optStringOrNull("status"),
			summary = obj.optStringOrNull("summary"),
			genres = obj.optStringOrNull("genres"),
			chapters = chapters,
		)
	}

	override fun close() {
		if (isClosed) return
		isClosed = true
		closeScope.launch {
			mutex.withLock {
				quickJs?.close()
				quickJs = null
			}
		}
	}
}

class LnPluginRuntimeClosedException(pluginId: String) :
	IllegalStateException("LnPluginRuntime[$pluginId] is closed")

/**
 * Factory for creating LnPluginRuntime instances.
 */
@javax.inject.Singleton
class LnPluginRuntimeFactory @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
) {
	fun create(pluginId: String, pluginScript: String, pluginSite: String = ""): LnPluginRuntime {
		return LnPluginRuntime(context, httpClient, pluginId, pluginScript, pluginSite)
	}
}
