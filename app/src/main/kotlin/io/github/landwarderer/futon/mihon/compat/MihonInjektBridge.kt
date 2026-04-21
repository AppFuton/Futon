package io.github.landwarderer.futon.mihon.compat

import android.app.Application
import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import javax.inject.Singleton

@Singleton
class MihonInjektBridge(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
) {
    
    private val application: Application
        get() = context.applicationContext as Application
    
    @Volatile
    private var initialized = false
    
    /**
     * This must be called before loading any Mihon extensions.
     * 
     * Thread-safe - can be called multiple times.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return
        
        try {
            val networkHelper = MihonNetworkHelper(httpClient, cookieJar, webViewExecutor)
            Log.d(
                "MihonInjektBridge",
                "Creating MihonNetworkHelper with webViewExecutorPresent=${webViewExecutor != null}",
            )
            
            Injekt.importModule(object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    // Application and Context
                    addSingleton(application)
                    addSingletonFactory<Context> { context.applicationContext }
                    
                    // Network components
                    addSingletonFactory<NetworkHelper> { networkHelper }
                    addSingletonFactory<OkHttpClient> { httpClient }
                    addSingletonFactory<CookieJar> { cookieJar }
                    
                    // JSON - explicitly type it to ensure Injekt matches correctly
                    val json = Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                    addSingletonFactory<Json> { json }
                    addSingletonFactory<StringFormat> { json }
                    addSingletonFactory<SerialFormat> { json }
                }
            })
            
            initialized = true
            Log.d("MIhonInjektBridge", "Injekt initialized with App dependencies")
        } catch (e: Throwable) {
            Log.e("MihonInjektBridge", "CRITICAL: Failed to initialize Injekt bridge", e)
            // Do not rethrow, so the app can continue to function without Mihon
        }
    }
    
    /**
     * Check if Injekt has been initialized.
     */
    fun isInitialized(): Boolean = initialized
}
