package io.github.landwarderer.futon.mihon.extensions.repo

import android.util.Log
import androidx.annotation.Keep
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.GitHubMirror
import io.github.landwarderer.futon.mihon.MihonExtensionLoader
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class ExtensionRepoService @Inject constructor(
	@MangaHttpClient private val httpClient: OkHttpClient,
	private val json: Json,
	private val settings: AppSettings,
) {

	private val protoBuf = ProtoBuf {
		@Suppress("OPT_IN_USAGE")
		encodeDefaults = true
	}

	private fun applyMirror(url: String): String {
		if (url.startsWith("https://raw.githubusercontent.com/")) {
			return when (settings.gitHubMirror) {
				GitHubMirror.KEIYOUSHI -> {
					if (url.contains("/keiyoushi/extensions/")) {
						url.replace("raw.githubusercontent.com", "raw.github.com")
					} else {
						"https://raw.github.com/keiyoushi/extensions/refs/heads/repo/${url.substringAfter("raw.githubusercontent.com/")}"
					}
				}
				GitHubMirror.KKGITHUB -> url.replace("raw.githubusercontent.com", "raw.kkgithub.com")
				GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
				GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
				else -> url
			}
		}
		if (url.startsWith("https://github.com/")) {
			return when (settings.gitHubMirror) {
				GitHubMirror.KKGITHUB -> url.replace("github.com", "kkgithub.com")
				GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
				GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
				else -> url
			}
		}
		return url
	}

	private fun deriveRepoName(baseUrl: String, defaultName: String): String {
		val url = baseUrl.toHttpUrlOrNull() ?: return defaultName
		val segments = url.pathSegments.filter { it.isNotEmpty() }
		if (segments.size >= 2 && url.host.contains("githubusercontent.com")) {
			return "${segments[0]}/${segments[1]}"
		} else if (segments.size >= 2 && url.host == "github.com") {
			return "${segments[0]}/${segments[1]}"
		} else if (segments.isNotEmpty()) {
			return segments.last()
		}
		return url.host
	}

	suspend fun fetchRepoDetails(baseUrl: String, type: ExternalExtensionType): ExternalExtensionRepo {
		if (type == ExternalExtensionType.IREADER || type == ExternalExtensionType.JAR) {
			val now = System.currentTimeMillis()
			val derived = deriveRepoName(baseUrl, if (type == ExternalExtensionType.IREADER) "IReader" else "Futon")
			val repoName = if (type == ExternalExtensionType.IREADER) "IReader: $derived" else "Futon: $derived"
			val repoShort = derived
			var version: String? = null
			if (type == ExternalExtensionType.JAR) {
				val indexUrl = applyMirror("$baseUrl/index.min.json")
				runCatching {
					withTimeout(REPO_DETAILS_TIMEOUT_MS) {
						val body = httpClient.newCall(GET(indexUrl)).awaitSuccess().use { response ->
							response.body.string()
						}
						val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
						version = dto.firstOrNull()?.version
					}
				}
			}

			return ExternalExtensionRepo(
				type = type,
				baseUrl = baseUrl,
				name = repoName,
				shortName = repoShort,
				website = baseUrl,
				signingKeyFingerprint = baseUrl.hashCode().toString(16),
				createdAt = now,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
				version = version,
			)
		}

		val repoJsonUrl = applyMirror("$baseUrl/repo.json")
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchRepoDetails:start type=$type url=$repoJsonUrl")
		return withTimeout(REPO_DETAILS_TIMEOUT_MS) {
			val body = httpClient.newCall(GET(repoJsonUrl)).awaitSuccess().use { response ->
				response.body.string()
			}
			val dto = json.decodeFromString<RepoMetaWrapperDto>(body)
			val now = System.currentTimeMillis()
			ExternalExtensionRepo(
				type = type,
				baseUrl = baseUrl,
				name = dto.meta.name,
				shortName = dto.meta.shortName,
				website = dto.meta.website,
				signingKeyFingerprint = dto.meta.signingKeyFingerprint,
				createdAt = now,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
			)
		}.also { repo ->
			Log.d(
				TAG,
				"fetchRepoDetails:success type=$type baseUrl=${repo.baseUrl} name=${repo.displayName} elapsedMs=${System.currentTimeMillis() - startedAt}",
			)
		}
	}

	suspend fun fetchAvailableExtensions(repo: ExternalExtensionRepo): List<RepoAvailableExtension> {
		val startedAt = System.currentTimeMillis()
		Log.d(TAG, "fetchAvailableExtensions:start type=${repo.type} baseUrl=${repo.baseUrl}")

		if (repo.type == ExternalExtensionType.MIHON || repo.type == ExternalExtensionType.ANIYOMI) {
			val pbResult = runCatching {
				val pbUrl = applyMirror("${repo.baseUrl}/index.pb")
				Log.d(TAG, "fetchAvailableExtensions:trying index.pb url=$pbUrl")
				withTimeout(CATALOG_TIMEOUT_MS) {
					val rawBytes = httpClient.newCall(GET(pbUrl)).awaitSuccess().use { response ->
						response.body.bytes()
					}
					val decompressedBytes = if (rawBytes.size >= 2 && rawBytes[0] == 0x1f.toByte() && rawBytes[1] == 0x8b.toByte()) {
						GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
					} else {
						rawBytes
					}
					val storeDto = protoBuf.decodeFromByteArray<NetworkExtensionStoreDto>(decompressedBytes)
					val extensions = storeDto.extensionList?.extensions.orEmpty()
					extensions.asSequence()
						.filterNot { ext ->
							ext.packageName == "eu.kanade.tachiyomi.extension.all.outdated" ||
								ext.packageName.endsWith(".outdated") ||
								ext.name.startsWith("Outdated App", ignoreCase = true) ||
								ext.name.contains("Update to Mihon", ignoreCase = true)
						}
						.mapNotNull { ext -> ext.toAvailableExtension(repo) }
						.toList()
				}
			}

			if (pbResult.isSuccess && pbResult.getOrNull()?.isNotEmpty() == true) {
				val list = pbResult.getOrThrow()
				Log.d(
					TAG,
					"fetchAvailableExtensions:success (protobuf) type=${repo.type} baseUrl=${repo.baseUrl} count=${list.size} elapsedMs=${System.currentTimeMillis() - startedAt}",
				)
				return list
			} else if (pbResult.isFailure) {
				Log.d(TAG, "fetchAvailableExtensions:protobuf fallback to json, reason=${pbResult.exceptionOrNull()?.message}")
			}
		}

		val indexUrl = "${repo.baseUrl}/index.min.json"
		val requestUrl = applyMirror(indexUrl)
		Log.d(TAG, "fetchAvailableExtensions:trying json url=$requestUrl")
		return runCatching {
			withTimeout(CATALOG_TIMEOUT_MS) {
				val body = httpClient.newCall(GET(requestUrl)).awaitSuccess().use { response ->
					response.body.string()
				}
				if (repo.type == ExternalExtensionType.IREADER) {
					val dto = json.decodeFromString<List<IReaderExtensionIndexDto>>(body)
					dto.asSequence()
						.mapNotNull { item -> item.toAvailableExtension(repo) }
						.toList()
				} else {
					val dto = json.decodeFromString<List<ExtensionIndexDto>>(body)
					dto.asSequence()
						.mapNotNull { item -> item.toAvailableExtension(repo) }
						.toList()
				}
			}
		}.onSuccess { extensions ->
			Log.d(
				TAG,
				"fetchAvailableExtensions:success (json) type=${repo.type} baseUrl=${repo.baseUrl} count=${extensions.size} elapsedMs=${System.currentTimeMillis() - startedAt}",
			)
		}.onFailure { error ->
			Log.e(
				TAG,
				"fetchAvailableExtensions:failed type=${repo.type} baseUrl=${repo.baseUrl} elapsedMs=${System.currentTimeMillis() - startedAt} message=${error.message}",
				error,
			)
		}.getOrDefault(emptyList())
	}

	fun normalizeIndexUrl(input: String): String? {
		val processUrl = input.trim()

		var url = processUrl.toHttpUrlOrNull() ?: return null
		if (url.scheme != "https" && url.scheme != "http") {
			return null
		}

		if (url.host == "github.com") {
			val segments = url.pathSegments.filter { it.isNotEmpty() }
			if (segments.size >= 3 && (segments[2] == "raw" || segments[2] == "tree" || segments[2] == "blob" || segments[2] == "refs")) {
				val user = segments[0]
				val repo = segments[1]
				val rest = if (segments[2] == "refs" && segments.size >= 5 && segments[3] == "heads") {
					segments.drop(4)
				} else {
					segments.drop(3)
				}
				val newPath = "/$user/$repo/" + rest.joinToString("/")
				url = url.newBuilder()
					.host("raw.githubusercontent.com")
					.encodedPath(newPath)
					.build()
			}
		}

		val normalizedSegments = url.pathSegments
			.filter { it.isNotEmpty() }
			.toMutableList()
		if (normalizedSegments.lastOrNull() in listOf("index.min.json", "index.json", "index.pb", "repo.json")) {
			normalizedSegments[normalizedSegments.lastIndex] = "index.min.json"
		} else {
			normalizedSegments += "index.min.json"
		}
		val normalizedPath = "/" + normalizedSegments.joinToString("/")
		return url.newBuilder()
			.encodedPath(normalizedPath)
			.fragment(null)
			.query(null)
			.build()
			.toString()
	}

	fun baseUrlFromIndexUrl(indexUrl: String): String {
		return indexUrl.removeSuffix("/index.min.json")
	}

	private fun ExtensionDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
		val libVersion = runCatching {
			val parts = extensionLib.split('.')
			if (parts.size >= 2) {
				"${parts[0]}.${parts[1]}".toDouble()
			} else {
				parts[0].toDouble()
			}
		}.getOrNull() ?: return null

		val supported = when (repo.type) {
			ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.ANIYOMI -> libVersion in (1.2)..(2.5)
			ExternalExtensionType.IREADER -> true
			ExternalExtensionType.JAR -> true
		}

		val displayName = when (repo.type) {
			ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
			ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
			ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
			ExternalExtensionType.JAR -> name
		}

		val langs = sources.map { it.language }.filter { it.isNotEmpty() }.toSet()
		val lang = if (langs.size == 1) langs.first() else "all"

		val icon = if (resources.iconUrl.startsWith("http://") || resources.iconUrl.startsWith("https://")) {
			applyMirror(resources.iconUrl)
		} else if (resources.iconUrl.isNotEmpty()) {
			applyMirror("${repo.baseUrl}/${resources.iconUrl.trimStart('/')}")
		} else {
			applyMirror("${repo.baseUrl}/icon/${resources.apkUrl.substringAfterLast('/').replace(".apk", ".png")}")
		}

		return RepoAvailableExtension(
			type = repo.type,
			name = displayName,
			pkgName = packageName,
			versionName = versionName,
			versionCode = versionCode,
			libVersion = libVersion,
			lang = lang,
			isNsfw = contentWarning >= 2,
			sourceNames = sources.map { it.name },
			apkName = resources.apkUrl,
			iconUrl = icon,
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = repo.signingKeyFingerprint,
			isCompatible = supported,
		)
	}

	private fun ExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
		if (pkg == "eu.kanade.tachiyomi.extension.all.outdated" ||
			pkg.endsWith(".outdated") ||
			name.startsWith("Outdated App", ignoreCase = true) ||
			name.contains("Update to Mihon", ignoreCase = true)
		) {
			return null
		}

		val libVersion = runCatching {
			val parts = version.split('.')
			if (parts.size >= 2) {
				"${parts[0]}.${parts[1]}".toDouble()
			} else {
				parts[0].toDouble()
			}
		}.getOrNull() ?: if (repo.type == ExternalExtensionType.IREADER) 0.0 else return null

		val supported = when (repo.type) {
			ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
			ExternalExtensionType.ANIYOMI -> libVersion in (1.2)..(2.5)
			ExternalExtensionType.IREADER -> true
			ExternalExtensionType.JAR -> true
		}
		val displayName = when (repo.type) {
			ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
			ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
			ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
			ExternalExtensionType.JAR -> name
		}

		return RepoAvailableExtension(
			type = repo.type,
			name = displayName,
			pkgName = pkg,
			versionName = version,
			versionCode = code,
			libVersion = libVersion,
			lang = lang,
			isNsfw = nsfw == 1,
			sourceNames = sources.orEmpty().map { it.name },
			apkName = apk,
			iconUrl = applyMirror(if (repo.type == ExternalExtensionType.IREADER) "${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}" else "${repo.baseUrl}/icon/$pkg.png"),
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = repo.signingKeyFingerprint,
			isCompatible = supported,
		)
	}

	private fun IReaderExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension {
		val libVersion = runCatching {
			val parts = version.split('.')
			if (parts.size >= 2) {
				"${parts[0]}.${parts[1]}".toDouble()
			} else {
				parts[0].toDouble()
			}
		}.getOrNull() ?: 0.0
		val displayName = name.removePrefix("IReader: ")

		return RepoAvailableExtension(
			type = repo.type,
			name = displayName,
			pkgName = pkg,
			versionName = version,
			versionCode = code,
			libVersion = libVersion,
			lang = lang,
			isNsfw = nsfw,
			sourceNames = emptyList(),
			apkName = apk,
			iconUrl = applyMirror("${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}"),
			repoUrl = repo.baseUrl,
			repoName = repo.displayName,
			signatureHash = "",
			isCompatible = true,
		)
	}

	@Keep
	@Serializable
	private data class RepoMetaWrapperDto(
		@SerialName("index_v2")
		val indexV2: String? = null,
		val meta: RepoMetaDto,
	)

	@Keep
	@Serializable
	private data class RepoMetaDto(
		val name: String,
		@SerialName("shortName")
		val shortName: String? = null,
		val website: String,
		@SerialName("signingKeyFingerprint")
		val signingKeyFingerprint: String,
	)

	@Keep
	@Serializable
	private data class ExtensionIndexDto(
		val name: String,
		val pkg: String,
		val apk: String,
		val lang: String = "all",
		val code: Long,
		val version: String,
		val nsfw: Int = 0,
		val sources: List<ExtensionSourceDto>? = null,
	)

	@Keep
	@Serializable
	private data class ExtensionSourceDto(
		val name: String,
	)

	@Keep
	@Serializable
	private data class IReaderExtensionIndexDto(
		val name: String = "",
		val pkg: String = "",
		val apk: String = "",
		val lang: String = "en",
		val code: Long = 1,
		val version: String = "1.0",
		val nsfw: Boolean = false,
	)

	@Keep
	@Serializable
	private data class NetworkExtensionStoreDto(
		@ProtoNumber(1) val name: String = "",
		@ProtoNumber(2) val badgeLabel: String = "",
		@ProtoNumber(3) val signingKey: String = "",
		@ProtoNumber(4) val contact: ContactDto? = null,
		@ProtoNumber(101) val extensionList: ExtensionListDto? = null,
		@ProtoNumber(102) val extensionListUrl: String? = null,
	)

	@Keep
	@Serializable
	private data class ContactDto(
		@ProtoNumber(1) val website: String = "",
		@ProtoNumber(2) val discord: String? = null,
	)

	@Keep
	@Serializable
	private data class ExtensionListDto(
		@ProtoNumber(1) val extensions: List<ExtensionDto> = emptyList(),
	)

	@Keep
	@Serializable
	private data class ExtensionDto(
		@ProtoNumber(1) val name: String = "",
		@ProtoNumber(2) val packageName: String = "",
		@ProtoNumber(3) val resources: ResourcesDto = ResourcesDto(),
		@ProtoNumber(4) val extensionLib: String = "",
		@ProtoNumber(5) val versionCode: Long = 0L,
		@ProtoNumber(6) val versionName: String = "",
		@ProtoNumber(7) val contentWarning: Int = 0,
		@ProtoNumber(8) val sources: List<SourceDto> = emptyList(),
	)

	@Keep
	@Serializable
	private data class ResourcesDto(
		@ProtoNumber(1) val apkUrl: String = "",
		@ProtoNumber(2) val iconUrl: String = "",
	)

	@Keep
	@Serializable
	private data class SourceDto(
		@ProtoNumber(1) val id: Long = 0L,
		@ProtoNumber(2) val name: String = "",
		@ProtoNumber(3) val language: String = "",
		@ProtoNumber(4) val homeUrl: String = "",
		@ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
		@ProtoNumber(7) val message: String? = null,
	)

	private companion object {
		const val TAG = "ExtensionRepo"
		const val REPO_DETAILS_TIMEOUT_MS = 15_000L
		const val CATALOG_TIMEOUT_MS = 20_000L
	}
}
