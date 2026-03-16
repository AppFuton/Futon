package io.github.landwarderer.futon.core.network

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.core.network.cookies.AndroidCookieJar
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import io.github.landwarderer.futon.core.network.cookies.PreferencesCookieJar
import io.github.landwarderer.futon.core.network.imageproxy.ImageProxyInterceptor
import io.github.landwarderer.futon.core.network.imageproxy.RealImageProxyInterceptor
import io.github.landwarderer.futon.core.network.proxy.ProxyProvider
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.assertNotInMainThread
import io.github.landwarderer.futon.local.data.LocalStorageManager
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {

    @Binds
    fun bindCookieJar(androidCookieJar: MutableCookieJar): CookieJar

    @Binds
    fun bindImageProxyInterceptor(impl: RealImageProxyInterceptor): ImageProxyInterceptor

    companion object {

        @Provides
        @Singleton
        fun provideCookieJar(
            @ApplicationContext context: Context
        ): MutableCookieJar = runCatching {
            AndroidCookieJar()
        }.getOrElse { e ->
            Log.e("NetworkModule::provideCookieJar", e.stackTraceToString())
            // WebView is not available
            PreferencesCookieJar(context)
        }

        @Provides
        @Singleton
        fun provideHttpCache(
            localStorageManager: LocalStorageManager,
        ): Cache = localStorageManager.createHttpCache()

        @Provides
        @Singleton
        @BaseHttpClient
        fun provideBaseHttpClient(
            @ApplicationContext contextProvider: Provider<Context>,
            cache: Cache,
            cookieJar: CookieJar,
            settings: AppSettings,
            proxyProvider: ProxyProvider,
        ): OkHttpClient = OkHttpClient.Builder().apply {
            assertNotInMainThread()
            connectTimeout(20, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(20, TimeUnit.SECONDS)
            cookieJar(cookieJar)
            proxySelector(proxyProvider.selector)
            proxyAuthenticator(proxyProvider.authenticator)
            dns(DoHManager(cache, settings))
            installExtraCertificates(contextProvider.get())
            applyTlsConfiguration()
            cache(cache)
            addInterceptor(CloudFlareInterceptor())
            addInterceptor(RateLimitInterceptor())
            if (BuildConfig.DEBUG) {
                addInterceptor(CurlLoggingInterceptor())
            }
        }.build()

        @Provides
        @Singleton
        @MangaHttpClient
        fun provideMangaHttpClient(
            @BaseHttpClient baseClient: OkHttpClient,
            commonHeadersInterceptor: CommonHeadersInterceptor,
        ): OkHttpClient = baseClient.newBuilder().apply {
            addNetworkInterceptor(CacheLimitInterceptor())
            addInterceptor(commonHeadersInterceptor)
        }.build()

    }
}
