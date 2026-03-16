package io.github.landwarderer.futon.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.parsers.util.await
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

private const val BUILD_TYPE_RELEASE = "release"

@Singleton
class AppUpdateRepository @Inject constructor(
	private val settings: AppSettings,
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext context: Context,
) {
// TODO("Fix update checking.")
	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val latestReleaseUrl = buildString {
		append("https://api.github.com/repos/")
		append(context.getString(R.string.github_updates_repo))
		append("/releases/latest")
	}

	private val changelogUrl = buildString {
		append("https://raw.githubusercontent.com/")
		append(context.getString(R.string.github_updates_repo))
		append("/refs/heads/devel/CHANGELOG.md")
	}

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(latestReleaseUrl)
				.build()
			val response = okHttp.newCall(request).await()
			val json = JSONObject(response.body?.string() ?: "{}")
			
			val currentVersion = VersionId(BuildConfig.VERSION_NAME)
			val releaseVersion = VersionId(json.getString("name").removePrefix("v"))
			
			// Only return update if there's a newer version available
			if (releaseVersion <= currentVersion) {
				return@runCatchingCancellable null
			}
			
			AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = json.getString("name").removePrefix("v"),
				apkSize = 0L, // No longer downloading, so size not needed
				apkUrl = "", // No longer downloading
				description = json.getString("body"),
			)
		}.onFailure {
			it.printStackTraceDebug()
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	suspend fun fetchChangelog(): String? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(changelogUrl)
				.build()
			okHttp.newCall(request).await().body?.string()
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	@Suppress("KotlinConstantConditions")
	suspend fun isUpdateSupported(): Boolean {
		return true // Updates are always available now (just checking for newer version)
	}
}
