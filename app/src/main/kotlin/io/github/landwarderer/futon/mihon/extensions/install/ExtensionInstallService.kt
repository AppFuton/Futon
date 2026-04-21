package io.github.landwarderer.futon.mihon.extensions.install

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.GitHubMirror
import io.github.landwarderer.futon.mihon.extensions.repo.RepoAvailableExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionInstallService @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
	private val settings: AppSettings,
) {

	private fun applyMirror(url: String): String {
		if (url.startsWith("https://raw.githubusercontent.com/")) {
			return when (settings.gitHubMirror) {
				GitHubMirror.NATIVE -> url
				GitHubMirror.KKGITHUB -> url.replace("raw.githubusercontent.com", "raw.kkgithub.com")
				GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
				GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
			}
		}
		return url
	}

	private val activeCalls = ConcurrentHashMap<String, Call>()
	private val _downloadStates = MutableStateFlow<Map<String, ExtensionInstallDownloadState>>(emptyMap())

	val downloadStates: StateFlow<Map<String, ExtensionInstallDownloadState>> = _downloadStates.asStateFlow()

	suspend fun createInstallIntent(extension: RepoAvailableExtension): Intent? {
		val apkUrl = applyMirror("${extension.repoUrl}/apk/${extension.apkName}")
		val outputDir = File(context.cacheDir, "extension-installs").apply { mkdirs() }
		val outputFile = File(outputDir, "${extension.pkgName}-${extension.versionCode}.apk")
		val call = httpClient.newCachelessCallWithProgress(GET(apkUrl), ExtensionInstallProgressListener(extension.pkgName))
		check(activeCalls.putIfAbsent(extension.pkgName, call) == null) {
			"Extension install download already in progress for ${extension.pkgName}"
		}
		updateDownloadState(extension.pkgName, bytesRead = 0L, contentLength = -1L)
		try {
			call.awaitSuccess().use { response ->
				val body = requireNotNull(response.body) { "Missing APK response body" }
				outputFile.outputStream().use { output ->
					body.byteStream().use { input ->
						input.copyTo(output)
					}
				}
			}
		} catch (e: IOException) {
			if (call.isCanceled()) {
				throw CancellationException("Extension install download cancelled for ${extension.pkgName}", e)
			}
			throw e
		} finally {
			activeCalls.remove(extension.pkgName)
			_downloadStates.update { it - extension.pkgName }
		}
		
		if (extension.type == io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType.JAR) {
			val pluginsDir = File(context.filesDir, "plugins").apply { mkdirs() }
			val jarFile = File(pluginsDir, "${extension.pkgName}.jar")
			outputFile.copyTo(jarFile, overwrite = true)
			outputFile.delete()
			context.getSharedPreferences("jar_plugin_versions", Context.MODE_PRIVATE)
				.edit {
                    putLong(extension.pkgName, extension.versionCode)
                }
			return null
		}
		
		val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", outputFile)
		return Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
		}
	}

	fun cancelDownload(packageName: String) {
		activeCalls[packageName]?.cancel()
	}

	private fun updateDownloadState(packageName: String, bytesRead: Long, contentLength: Long) {
		_downloadStates.update { states ->
			states + (packageName to ExtensionInstallDownloadState(packageName, bytesRead, contentLength))
		}
	}

	private inner class ExtensionInstallProgressListener(
		private val packageName: String,
	) : ProgressListener {

		override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
			updateDownloadState(packageName, bytesRead, contentLength)
		}
	}
}

data class ExtensionInstallDownloadState(
	val packageName: String,
	val bytesRead: Long,
	val contentLength: Long,
) {

	val progressPercent: Int?
		get() = if (contentLength <= 0L) {
			null
		} else {
			((bytesRead * 100L) / contentLength)
				.coerceIn(0L, 100L)
				.toInt()
		}
}
