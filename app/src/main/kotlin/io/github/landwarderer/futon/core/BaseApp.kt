package io.github.landwarderer.futon.core

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.internal.platform.PlatformRegistry

import org.conscrypt.Conscrypt
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.os.AppValidator
import io.github.landwarderer.futon.core.os.RomCompat
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.local.data.LocalStorageChanges
import io.github.landwarderer.futon.local.data.index.LocalMangaIndex
import io.github.landwarderer.futon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import io.github.landwarderer.futon.settings.work.WorkScheduleManager
import java.security.Security
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
open class BaseApp : Application(), Configuration.Provider {

	@Inject
	lateinit var databaseObserversProvider: Provider<Set<@JvmSuppressWildcards InvalidationTracker.Observer>>

	@Inject
	lateinit var activityLifecycleCallbacks: Set<@JvmSuppressWildcards ActivityLifecycleCallbacks>

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var appValidator: AppValidator

	@Inject
	lateinit var workScheduleManager: WorkScheduleManager

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun onCreate() {
		super.onCreate()
		PlatformRegistry.applicationContext = this // TODO replace with OkHttp.initialize
		AppCompatDelegate.setDefaultNightMode(settings.theme)
		// Initialize Sentry only if user has opted in
		if (settings.isCrashAnalyticsEnabled) {
			initializeSentry()
		}
		// TLS 1.3 support for Android < 10
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Security.insertProviderAt(Conscrypt.newProvider(), 1)
		}
		setupActivityLifecycleCallbacks()
		processLifecycleScope.launch(Dispatchers.IO) {
			setupDatabaseObservers()
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
		workScheduleManager.init()
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		// ACRA removed
	} 

	@WorkerThread
	private fun setupDatabaseObservers() {
		val tracker = database.get().invalidationTracker
		databaseObserversProvider.get().forEach {
			tracker.addObserver(it)
		}
	}

	private fun setupActivityLifecycleCallbacks() {
		activityLifecycleCallbacks.forEach {
			registerActivityLifecycleCallbacks(it)
		}
	}

	private fun initializeSentry() {
		try {
			io.sentry.android.core.SentryAndroid.init(this) { options ->
				// DSN is read from BuildConfig which gets it from SENTRY_DSN environment variable
				// Only set if DSN is provided (non-empty)
				if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
					options.dsn = BuildConfig.SENTRY_DSN
					options.isEnableAutoSessionTracking = true
					options.environment = if (BuildConfig.DEBUG) "debug" else "production"
				}
				options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
					val exceptions = event.exceptions
					if (exceptions != null && exceptions.any { it.isHttpError() }) null else event
				}
			}
		} catch (e: Exception) {
			// Log error but don't crash if Sentry initialization fails
			e.printStackTrace()
		}
	}

	private fun io.sentry.protocol.SentryException.isHttpError(): Boolean {
		val name = type ?: return false
		return name == "HttpException" ||
			name.endsWith(".HttpException") ||
			name == "SentryHttpClientException" ||
			name == "SocketTimeoutException" ||
			name == "UnknownHostException" ||
			name == "ConnectException" ||
			name == "SSLException"
	}
}
