package io.github.landwarderer.futon.mihon

import android.util.Log
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.GitHubMirror
import io.github.landwarderer.futon.mihon.extensions.repo.ExtensionRepoService
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepo
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType

class ExtensionRepoServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private lateinit var appSettings: AppSettings
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        appSettings = Mockito.mock(AppSettings::class.java)
        Mockito.`when`(appSettings.gitHubMirror).thenReturn(GitHubMirror.NATIVE)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun testFetchAvailableExtensions_filtersOutdatedAndParsesLibVersions() = runTest {
        val jsonCatalog = """
            [
                {
                    "name": "Outdated App",
                    "pkg": "eu.kanade.tachiyomi.extension.all.outdated",
                    "apk": "tachiyomi-outdated.apk",
                    "lang": "all",
                    "code": 1,
                    "version": "0.20.1",
                    "nsfw": 0
                },
                {
                    "name": "Tachiyomi: MangaFire",
                    "pkg": "eu.kanade.tachiyomi.extension.all.mangafire",
                    "apk": "tachiyomi-mangafire.apk",
                    "lang": "all",
                    "code": 12,
                    "version": "1.5",
                    "nsfw": 0
                },
                {
                    "name": "Tachiyomi: HentaiFox",
                    "pkg": "eu.kanade.tachiyomi.extension.all.hentaifox",
                    "apk": "tachiyomi-hentaifox.apk",
                    "lang": "all",
                    "code": 10,
                    "version": "1.4.12",
                    "nsfw": 1
                },
                {
                    "name": "Tachiyomi: Asura Scans",
                    "pkg": "eu.kanade.tachiyomi.extension.en.asurascans",
                    "apk": "tachiyomi-asurascans.apk",
                    "lang": "en",
                    "code": 20,
                    "version": "2.0.1",
                    "nsfw": 0
                },
                {
                    "name": "Tachiyomi: Old Extension",
                    "pkg": "eu.kanade.tachiyomi.extension.all.oldext",
                    "apk": "tachiyomi-oldext.apk",
                    "lang": "all",
                    "code": 5,
                    "version": "1.0.0",
                    "nsfw": 0
                },
                {
                    "name": "Tachiyomi: Future Extension",
                    "pkg": "eu.kanade.tachiyomi.extension.all.futureext",
                    "apk": "tachiyomi-futureext.apk",
                    "lang": "all",
                    "code": 30,
                    "version": "3.0.0",
                    "nsfw": 0
                }
            ]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher(com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService()))
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonCatalog.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = ExtensionRepoService(mockClient, json, appSettings)
        val repo = ExternalExtensionRepo(
            type = ExternalExtensionType.MIHON,
            baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
            name = "Keiyoushi",
            shortName = "Keiyoushi",
            website = "https://keiyoushi.github.io",
            signingKeyFingerprint = "abcd1234efgh5678",
            createdAt = 1000L,
            updatedAt = 1000L,
            lastSuccessAt = 1000L,
            lastError = null,
        )

        val result = service.fetchAvailableExtensions(repo)

        // Verify outdated notice package is filtered out
        assertNull(result.find { it.pkgName == "eu.kanade.tachiyomi.extension.all.outdated" })
        assertNull(result.find { it.name == "Outdated App" })

        // 5 valid extensions remain
        assertEquals(5, result.size)

        // Check 2-part version parsing ("1.5" -> libVersion 1.5)
        val mangafire = result.first { it.pkgName == "eu.kanade.tachiyomi.extension.all.mangafire" }
        assertEquals("MangaFire", mangafire.name)
        assertEquals(1.5, mangafire.libVersion, 0.001)
        assertTrue(mangafire.isCompatible)

        // Check 3-part version parsing ("1.4.12" -> libVersion 1.4)
        val hentaifox = result.first { it.pkgName == "eu.kanade.tachiyomi.extension.all.hentaifox" }
        assertEquals("HentaiFox", hentaifox.name)
        assertEquals(1.4, hentaifox.libVersion, 0.001)
        assertTrue(hentaifox.isNsfw)
        assertTrue(hentaifox.isCompatible)

        // Check 2.0 version parsing ("2.0.1" -> libVersion 2.0)
        val asurascans = result.first { it.pkgName == "eu.kanade.tachiyomi.extension.en.asurascans" }
        assertEquals("Asura Scans", asurascans.name)
        assertEquals(2.0, asurascans.libVersion, 0.001)
        assertTrue(asurascans.isCompatible)

        // Check too old version (1.0 < LIB_VERSION_MIN 1.2)
        val oldExt = result.first { it.pkgName == "eu.kanade.tachiyomi.extension.all.oldext" }
        assertEquals(1.0, oldExt.libVersion, 0.001)
        assertFalse(oldExt.isCompatible)

        // Check too new version (3.0 > LIB_VERSION_MAX 2.5)
        val futureExt = result.first { it.pkgName == "eu.kanade.tachiyomi.extension.all.futureext" }
        assertEquals(3.0, futureExt.libVersion, 0.001)
        assertFalse(futureExt.isCompatible)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun testFetchAvailableExtensions_protobufGzipSupport() = runTest {
        // Construct a gzipped protobuf response using test data
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(baos).use { gzipOut ->
            // Write a simple protobuf payload or invoke fetch
            val sampleProto = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(
                TestStore.serializer(),
                ExtensionRepoServiceProtoTestHelper.sampleStore,
            )
            gzipOut.write(sampleProto)
        }
        val gzippedBytes = baos.toByteArray()

        val mockClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher(com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService()))
            .addInterceptor { chain ->
                val requestUrl = chain.request().url.toString()
                if (requestUrl.endsWith("/index.pb")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(gzippedBytes.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }
            }
            .build()

        val service = ExtensionRepoService(mockClient, json, appSettings)
        val repo = ExternalExtensionRepo(
            type = ExternalExtensionType.MIHON,
            baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
            name = "Keiyoushi",
            shortName = "Keiyoushi",
            website = "https://keiyoushi.github.io",
            signingKeyFingerprint = "abcd1234efgh5678",
            createdAt = 1000L,
            updatedAt = 1000L,
            lastSuccessAt = 1000L,
            lastError = null,
        )

        val result = service.fetchAvailableExtensions(repo)
        assertEquals(1, result.size)
        val ext = result.first()
        assertEquals("AHottie", ext.name)
        assertEquals("eu.kanade.tachiyomi.extension.all.ahottie", ext.pkgName)
        assertEquals(1.6, ext.libVersion, 0.001)
        assertTrue(ext.isCompatible)
    }

    @Test
    fun testFetchAvailableExtensions_realKeiyoushiIndexPb() = runTest {
        val pbFile = java.io.File("/tmp/index.pb")
        if (!pbFile.exists()) return@runTest
        val realBytes = pbFile.readBytes()

        val mockClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher(com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService()))
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(realBytes.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val service = ExtensionRepoService(mockClient, json, appSettings)
        val repo = ExternalExtensionRepo(
            type = ExternalExtensionType.MIHON,
            baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
            name = "Keiyoushi",
            shortName = "Keiyoushi",
            website = "https://keiyoushi.github.io",
            signingKeyFingerprint = "abcd1234efgh5678",
            createdAt = 1000L,
            updatedAt = 1000L,
            lastSuccessAt = 1000L,
            lastError = null,
        )

        val result = service.fetchAvailableExtensions(repo)
        assertTrue("Real Keiyoushi repo should have > 1000 extensions, got ${result.size}", result.size > 1000)
    }

    @Test
    fun testNormalizeIndexUrl() {
        val service = ExtensionRepoService(OkHttpClient(), json, appSettings)

        val normalized = service.normalizeIndexUrl("https://raw.githubusercontent.com/keiyoushi/extensions/repo")
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", normalized)

        val base = service.baseUrlFromIndexUrl(normalized!!)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo", base)

        // Test GitHub raw URL normalization
        val ghRaw = service.normalizeIndexUrl("https://github.com/keiyoushi/extensions/raw/repo/index.pb")
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", ghRaw)

        val ghTree = service.normalizeIndexUrl("https://github.com/keiyoushi/extensions/tree/repo")
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", ghTree)
    }
}

@kotlinx.serialization.Serializable
private data class TestStore(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val name: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(2) val badgeLabel: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(3) val signingKey: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(101) val extensionList: TestExtensionList? = null,
)

@kotlinx.serialization.Serializable
private data class TestExtensionList(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val extensions: List<TestExtension> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class TestExtension(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val name: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(2) val packageName: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(3) val resources: TestResources = TestResources(),
    @kotlinx.serialization.protobuf.ProtoNumber(4) val extensionLib: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(5) val versionCode: Long = 0L,
    @kotlinx.serialization.protobuf.ProtoNumber(6) val versionName: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(7) val contentWarning: Int = 0,
    @kotlinx.serialization.protobuf.ProtoNumber(8) val sources: List<TestSource> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class TestResources(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val apkUrl: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(2) val iconUrl: String = "",
)

@kotlinx.serialization.Serializable
private data class TestSource(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val id: Long = 0L,
    @kotlinx.serialization.protobuf.ProtoNumber(2) val name: String = "",
    @kotlinx.serialization.protobuf.ProtoNumber(3) val language: String = "",
)

private object ExtensionRepoServiceProtoTestHelper {
    val sampleStore = TestStore(
        name = "Keiyoushi",
        badgeLabel = "KEI",
        signingKey = "abcd1234efgh5678",
        extensionList = TestExtensionList(
            extensions = listOf(
                TestExtension(
                    name = "Tachiyomi: AHottie",
                    packageName = "eu.kanade.tachiyomi.extension.all.ahottie",
                    resources = TestResources(
                        apkUrl = "https://github.com/keiyoushi/extensions/releases/download/a76c957-0/tachiyomi-all.ahottie-v1.6.4.apk",
                        iconUrl = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions-source@main/src/all/ahottie/res/mipmap-xhdpi/ic_launcher.png",
                    ),
                    extensionLib = "1.6",
                    versionCode = 1,
                    versionName = "1.6.4",
                    contentWarning = 0,
                    sources = listOf(
                        TestSource(id = 1L, name = "AHottie", language = "all"),
                    ),
                )
            )
        )
    )
}

