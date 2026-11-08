package io.github.landwarderer.futon.novel.data.lnreader.runtime

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.novel.data.lnreader.model.LnChapterItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnNovelItem
import io.github.landwarderer.futon.novel.data.lnreader.model.LnPlugin
import io.github.landwarderer.futon.novel.data.lnreader.model.LnSourceNovel
import io.github.landwarderer.futon.novel.data.lnreader.storage.LnPluginStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import javax.inject.Inject

/**
 * Manages an isolated QuickJS execution context to run an LNReader JavaScript plugin.
 */
class LnPluginRuntime(
	private val context: Context,
	private val httpClient: OkHttpClient,
	private val pluginId: String,
	private val pluginScript: String,
) : Closeable {

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
	)

	private var quickJs: QuickJs? = null
	private val mutex = Mutex()
	private val prefs by lazy {
		context.getSharedPreferences("ln_plugin_$pluginId", Context.MODE_PRIVATE)
	}

	private suspend fun ensureInitialized() {
		if (quickJs != null) return

		val qjs = QuickJs.create(jobDispatcher = Dispatchers.IO)

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
			qjs.close()
			throw IllegalStateException("Failed to initialize LNReader plugin: $pluginId")
		}

		quickJs = qjs
	}

	suspend fun getPopularNovels(page: Int): List<LnNovelItem> = mutex.withLock {
		withContext(Dispatchers.IO) {
			ensureInitialized()
			val qjs = checkNotNull(quickJs)
			val script = """
				(async () => {
					const options = (typeof __futon_build_default_options === 'function')
						? __futon_build_default_options(__futon_current_plugin)
						: { showLatestNovels: false, filters: {} };
					const novels = await __futon_current_plugin.popularNovels($page, options);
					return JSON.stringify(novels || []);
				})()
			""".trimIndent()

			val jsonResult = qjs.evaluate<String>(script)
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
					const options = (typeof __futon_build_default_options === 'function')
						? __futon_build_default_options(__futon_current_plugin)
						: { showLatestNovels: false, filters: {} };
					const novels = await __futon_current_plugin.searchNovels($quotedQuery, $page, options);
					return JSON.stringify(novels || []);
				})()
			""".trimIndent()

			val jsonResult = qjs.evaluate<String>(script)
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

			val jsonResult = qjs.evaluate<String>(script)
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

			qjs.evaluate<String>(script)
		}
	}

	private suspend fun executeNativeFetch(
		url: String,
		method: String,
		headersJson: String,
		body: String?,
	): String = withContext(Dispatchers.IO) {
		val requestBuilder = Request.Builder().url(url)

		if (headersJson.isNotBlank()) {
			val headersObj = runCatching { JSONObject(headersJson) }.getOrNull()
			if (headersObj != null) {
				val keys = headersObj.keys()
				while (keys.hasNext()) {
					val key = keys.next()
					requestBuilder.header(key, headersObj.getString(key))
				}
			}
		}

		if (method.equals("POST", ignoreCase = true)) {
			val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()
			requestBuilder.post((body ?: "").toRequestBody(mediaType))
		} else {
			requestBuilder.method(method, null)
		}

		try {
			httpClient.newCall(requestBuilder.build()).execute().use { response ->
				val bodyString = response.body?.string().orEmpty()
				val headersMap = JSONObject()
				for (headerName in response.headers.names()) {
					headersMap.put(headerName.lowercase(), response.header(headerName))
				}

				JSONObject().apply {
					put("url", response.request.url.toString())
					put("status", response.code)
					put("body", bodyString)
					put("headers", headersMap)
				}.toString()
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			JSONObject().apply {
				put("status", 500)
				put("body", "")
				put("headers", JSONObject())
			}.toString()
		}
	}

	private fun parseNovelItems(jsonString: String): List<LnNovelItem> {
		val array = runCatching { JSONArray(jsonString) }.getOrElse { JSONArray() }
		val list = ArrayList<LnNovelItem>(array.length())
		for (i in 0 until array.length()) {
			val obj = array.getJSONObject(i)
			list.add(
				LnNovelItem(
					name = obj.optString("name", "Untitled"),
					path = obj.optString("path", ""),
					cover = obj.optString("cover").takeIf { !it.isNullOrBlank() },
				)
			)
		}
		return list
	}

	private fun parseSourceNovel(jsonString: String): LnSourceNovel {
		val obj = JSONObject(jsonString)
		val chaptersArray = obj.optJSONArray("chapters") ?: JSONArray()
		val chapters = ArrayList<LnChapterItem>(chaptersArray.length())
		for (i in 0 until chaptersArray.length()) {
			val cObj = chaptersArray.getJSONObject(i)
			val chapNum = if (cObj.has("chapterNumber") && !cObj.isNull("chapterNumber")) {
				cObj.optDouble("chapterNumber", 0.0).toFloat()
			} else null

			chapters.add(
				LnChapterItem(
					name = cObj.optString("name", "Chapter ${i + 1}"),
					path = cObj.optString("path", ""),
					chapterNumber = chapNum,
					releaseTime = cObj.optString("releaseTime").takeIf { !it.isNullOrBlank() },
				)
			)
		}

		return LnSourceNovel(
			name = obj.optString("name", "Untitled"),
			path = obj.optString("path", ""),
			cover = obj.optString("cover").takeIf { !it.isNullOrBlank() },
			author = obj.optString("author").takeIf { !it.isNullOrBlank() },
			artist = obj.optString("artist").takeIf { !it.isNullOrBlank() },
			status = obj.optString("status").takeIf { !it.isNullOrBlank() },
			summary = obj.optString("summary").takeIf { !it.isNullOrBlank() },
			genres = obj.optString("genres").takeIf { !it.isNullOrBlank() },
			chapters = chapters,
		)
	}

	override fun close() {
		quickJs?.close()
		quickJs = null
	}
}

/**
 * Factory for creating LnPluginRuntime instances.
 */
@javax.inject.Singleton
class LnPluginRuntimeFactory @Inject constructor(
	@ApplicationContext private val context: Context,
	private val httpClient: OkHttpClient,
) {
	fun create(pluginId: String, pluginScript: String): LnPluginRuntime {
		return LnPluginRuntime(context, httpClient, pluginId, pluginScript)
	}
}

