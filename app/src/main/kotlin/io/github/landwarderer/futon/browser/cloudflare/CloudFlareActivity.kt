package io.github.landwarderer.futon.browser.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.browser.BaseBrowserActivity
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.exceptions.resolve.CaptchaAutoResolveCoordinator
import io.github.landwarderer.futon.core.exceptions.resolve.CaptchaHandler
import io.github.landwarderer.futon.core.model.MangaSource
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import io.github.landwarderer.futon.core.network.webview.CF_STATE_JS
import io.github.landwarderer.futon.core.parser.ParserMangaRepository
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
open class CloudFlareActivity : BaseBrowserActivity(), CloudFlareCallback {

	protected open val isHiddenAutoResolveActivity = false

	private var pendingResult = RESULT_CANCELED
	private val isAutoResolve: Boolean by lazy { intent?.getBooleanExtra(EXTRA_AUTO_RESOLVE, false) == true }
	val sessionId: Long by lazy { intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L }
	private var resultNotified = false
	private val autoRecreateCount: Int by lazy {
		intent?.getIntExtra(EXTRA_AUTO_RECREATE_COUNT, 0) ?: 0
	}
	private var recreateRequested = false
	private var clearanceAtLaunch: String? = null
	private var clearanceUpdateObservedAt = 0L
	private var challengeStartedAt = 0L
	private var resolveJob: Job? = null
	private var isHiddenPresentation = false
	private var hiddenBackPressedCallback: OnBackPressedCallback? = null

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	@Inject
	lateinit var captchaAutoResolveCoordinator: CaptchaAutoResolveCoordinator

	private lateinit var cfClient: CloudFlareClient
	private var alignedUserAgent: String? = null

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: ParserMangaRepository?) {
		if (isHiddenAutoResolveActivity) {
			isHiddenPresentation = true
			// Keep the window focused and the WebView rendered: Turnstile treats a non-focused or
			// detached WebView differently. The translucent theme exposes the previous screen.
			viewBinding.appbar.isGone = true
			viewBinding.root.alpha = HIDDEN_RENDER_ALPHA
			window.setDimAmount(0f)
			window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
			// Back must cancel the invisible solver, not navigate its WebView history.
			hiddenBackPressedCallback = object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() = cancelAutomaticResolve()
			}.also { onBackPressedDispatcher.addCallback(this, it) }
			(this as? CloudFlareHiddenActivity)?.let {
				captchaAutoResolveCoordinator.registerHiddenActivity(it, sessionId)
			}
			if (isFinishing) {
				return
			}
		} else {
			setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		}
		val url = intent?.dataString
		if (url.isNullOrEmpty()) {
			finishAfterTransition()
			return
		}
		clearanceAtLaunch = CloudFlareHelper.getClearanceCookie(cookieJar, url)
		alignUserAgentWithEngine(repository)

		// Check if source needs header interception
		val needsInterception = shouldUseInterception(source, repository)
		Log.d(TAG, "Source: ${source.name}, needsInterception: $needsInterception")

		cfClient = if (needsInterception) {
			Log.d(TAG, "Using CloudFlareInterceptClient with header filtering")
			CloudFlareInterceptClient(cookieJar, this, adBlock, url)
		} else {
			Log.d(TAG, "Using regular CloudFlareClient (no interception)")
			CloudFlareClient(cookieJar, this, adBlock, url)
		}

		viewBinding.webView.webViewClient = cfClient
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null || autoRecreateCount > 0) {
				if (isAutoResolve && autoRecreateCount == 0) {
					// The clearance OkHttp has been presenting was rejected: do not let it short-circuit
					// the challenge in the WebView.
					url.toHttpUrlOrNull()?.let {
						clearRejectedClearance(it)
						CookieManager.getInstance().flush()
					}
				}
				awaitChallengeViewport()
				onTitleChanged(getString(R.string.loading_), url)
				// Arm the resolve budget only now: the proxy config and the viewport wait above can take a while.
				challengeStartedAt = System.currentTimeMillis()
				viewBinding.webView.loadUrl(url)
			} else {
				// Restored after a configuration change: the WebView kept its page.
				challengeStartedAt = System.currentTimeMillis()
			}
		}
		// A cf_clearance change is not a completion signal. Cloudflare can update it while the
		// interstitial is still running, so only the loaded page state may complete this flow.
		resolveJob = lifecycleScope.launch { runResolveLoop() }
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		if (isHiddenPresentation) return false
		menuInflater.inflate(R.menu.opt_captcha, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_retry -> {
			restartCheck()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun finish() {
		setResult(pendingResult)
		notifyAutoResolveResult()
		super.finish()
	}

	override fun onDestroy() {
		if (!isChangingConfigurations && !recreateRequested) {
			// Destroyed without finish(): the coordinator must not wait for this solver any longer.
			notifyAutoResolveResult()
		}
		(this as? CloudFlareHiddenActivity)?.let {
			captchaAutoResolveCoordinator.unregisterHiddenActivity(it)
		}
		super.onDestroy()
	}

	/**
	 * In auto-resolve mode the originating screen may already be gone, so its ActivityResultLauncher won't
	 * deliver the result. Notify the singleton coordinator instead.
	 */
	private fun notifyAutoResolveResult() {
		if (isAutoResolve && !resultNotified) {
			resultNotified = true
			captchaAutoResolveCoordinator.notifyResolveResult(sessionId, pendingResult == RESULT_OK)
		}
	}

	/** True only while this same WebView should remain invisible and at the top of the task. */
	fun shouldStayHiddenAndFocused(): Boolean =
		isAutoResolve && isHiddenPresentation && !isFinishing && !isDestroyed

	/** Cancels the automatic session without leaving a hidden Activity or WebView behind. */
	fun cancelAutomaticResolve() {
		if (!isAutoResolve || isFinishing || isDestroyed) return
		resolveJob?.cancel()
		if (hasViewBinding()) {
			viewBinding.webView.stopLoading()
		}
		finishAfterTransition()
	}

	override fun onLoadingStateChanged(isLoading: Boolean) = Unit

	override fun onPageLoaded() {
		viewBinding.progressBar.isInvisible = true
	}

	// The resolve loop decides when the challenge is over; a reloading interstitial is not a failure by itself.
	override fun onLoopDetected() = Unit

	override fun onCheckPassed() {
		if (isAutoResolve && autoRecreateCount < MAX_AUTO_RECREATE_COUNT) {
			markClearanceUpdateObserved()
		}
	}

	/**
	 * Do not start Turnstile until this Activity has a real viewport. The focus wait is bounded: a hidden
	 * solver that never wins focus must still load its URL.
	 */
	private suspend fun awaitChallengeViewport() {
		val focusDeadline = System.currentTimeMillis() + VIEWPORT_FOCUS_TIMEOUT_MS
		while (!viewBinding.webView.hasWindowFocus() && System.currentTimeMillis() < focusDeadline) {
			delay(VIEWPORT_POLL_INTERVAL_MS)
		}
		suspendCancellableCoroutine { cont ->
			viewBinding.webView.doOnLayout {
				if (cont.isActive) cont.resume(Unit)
			}
		}
		suspendCancellableCoroutine { cont ->
			viewBinding.webView.postOnAnimation {
				if (cont.isActive) cont.resume(Unit)
			}
		}
	}

	/**
	 * Drives the challenge to completion. The target page must report [CF_STATE_JS] == "ok" for several
	 * consecutive probes, so the short DOM gap between two managed-challenge stages is not accepted.
	 * The probe is a passive, read-only DOM query of the parent page and does not perturb the challenge.
	 */
	private suspend fun runResolveLoop() {
		val budget = if (autoRecreateCount < MAX_AUTO_RECREATE_COUNT) {
			AUTO_RETRY_DELAY_MS
		} else {
			MANUAL_FALLBACK_DELAY_MS
		}
		var consecutivePasses = 0
		val requiredStablePasses = if (isHiddenAutoResolveActivity && autoRecreateCount > 0) {
			HIDDEN_RECREATED_STABLE_PASSES
		} else {
			REQUIRED_STABLE_PASSES
		}
		while (true) {
			delay(RESOLVE_POLL_INTERVAL_MS)
			val startedAt = challengeStartedAt
			if (startedAt == 0L) {
				continue // nothing is loading yet
			}
			if (isAutoResolve && autoRecreateCount < MAX_AUTO_RECREATE_COUNT) {
				val clearance = intent.dataString?.let {
					CloudFlareHelper.getClearanceCookie(cookieJar, it)
				}
				if (clearance != null && clearance != clearanceAtLaunch) {
					markClearanceUpdateObserved()
				}
			}
			if (probeChallengeState() == CF_STATE_OK) {
				consecutivePasses++
				if (consecutivePasses >= requiredStablePasses) {
					finishSuccess()
					return
				}
			} else {
				consecutivePasses = 0
				val now = System.currentTimeMillis()
				val retryAt = startedAt + budget
				if (
					clearanceUpdateObservedAt != 0L &&
					now - clearanceUpdateObservedAt >= CLEARANCE_RECREATE_GRACE_MS
				) {
					requestAutoRecreate()
					return
				}
				if (isAutoResolve && autoRecreateCount < MAX_AUTO_RECREATE_COUNT && now >= retryAt) {
					requestAutoRecreate()
					return
				}
				if (isAutoResolve && autoRecreateCount >= MAX_AUTO_RECREATE_COUNT && now >= retryAt) {
					revealForManualCompletion()
				}
			}
		}
	}

	private fun markClearanceUpdateObserved() {
		if (clearanceUpdateObservedAt == 0L) {
			clearanceUpdateObservedAt = System.currentTimeMillis()
		}
	}

	/** Recreates the solver once, keeping the warmed-up browser profile (cookies, storage). */
	private fun requestAutoRecreate() {
		if (recreateRequested || autoRecreateCount >= MAX_AUTO_RECREATE_COUNT) return
		recreateRequested = true
		resolveJob?.cancel()
		CookieManager.getInstance().flush()
		intent.putExtra(EXTRA_AUTO_RECREATE_COUNT, autoRecreateCount + 1)
		recreate()
	}

	/** Falls back to manual completion without discarding the warmed browser profile or WebView. */
	private fun revealForManualCompletion() {
		if (!isHiddenPresentation) return
		isHiddenPresentation = false
		viewBinding.root.alpha = 1f
		viewBinding.appbar.isGone = false
		window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
		hiddenBackPressedCallback?.isEnabled = false
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		invalidateOptionsMenu()
	}

	private suspend fun probeChallengeState(): String = suspendCancellableCoroutine { cont ->
		viewBinding.webView.evaluateJavascript(CF_STATE_JS) { raw ->
			if (cont.isActive) {
				cont.resume(raw?.removeSurrounding("\"") ?: CF_STATE_WAIT)
			}
		}
	}

	private fun finishSuccess() {
		if (pendingResult == RESULT_OK) return
		pendingResult = RESULT_OK
		resolveJob?.cancel()
		rememberAlignedUserAgent()
		lifecycleScope.launch {
			// Persist the final cookie state before the WebView is destroyed and the parser retries.
			CookieManager.getInstance().flush()
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(MangaSource(source))
				}.onFailure {
					it.printStackTraceDebug("CloudFlareActivity::finishSuccess")
				}
			}
			finishAfterTransition()
		}
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		setTitle(title)
		supportActionBar?.subtitle = subtitle?.toString()?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }
	}

	private fun restartCheck() {
		lifecycleScope.launch {
			resolveJob?.cancel()
			viewBinding.webView.stopLoading()
			yield()
			cfClient.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				clearCfCookies(targetUrl)
				clearanceUpdateObservedAt = 0L
				challengeStartedAt = System.currentTimeMillis()
				viewBinding.webView.loadUrl(targetUrl.toString())
				resolveJob = lifecycleScope.launch { runResolveLoop() }
			}
		}
	}

	private suspend fun clearRejectedClearance(url: HttpUrl) = runInterruptible(Dispatchers.IO) {
		cookieJar.removeCookies(url) { cookie ->
			cookie.name == CLEARANCE_COOKIE_NAME
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.IO) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	/**
	 * Turnstile cannot be passed by a WebView that lies about what it is. Some parsers pin a desktop
	 * User-Agent, which contradicts everything the challenge script measures (`navigator.userAgentData`,
	 * platform, touch points, WebGL renderer), so the challenge reloads forever. Realign such an agent
	 * with the engine's own; sources whose agent already looks like Android are left untouched.
	 */
	private fun alignUserAgentWithEngine(repository: ParserMangaRepository?) {
		if (repository == null) return
		val engineUserAgent = runCatching { WebSettings.getDefaultUserAgent(this) }.getOrNull()
		if (engineUserAgent.isNullOrEmpty() || ANDROID_UA_TOKEN !in engineUserAgent) return
		val currentUserAgent = viewBinding.webView.settings.userAgentString
		if (currentUserAgent.isNullOrEmpty() || ANDROID_UA_TOKEN in currentUserAgent) return
		viewBinding.webView.settings.userAgentString = engineUserAgent
		alignedUserAgent = engineUserAgent
	}

	/**
	 * The clearance cookie is bound to the User-Agent that earned it, so OkHttp has to present the same
	 * one. Writing [ConfigKey.UserAgent] would not do, since parsers with a hard-coded agent never read it.
	 */
	private fun rememberAlignedUserAgent() {
		val userAgent = alignedUserAgent ?: return
		val source = intent?.getStringExtra(AppRouter.KEY_SOURCE) ?: return
		runCatching {
			(mangaRepositoryFactory.create(MangaSource(source)) as? ParserMangaRepository)
				?.getConfig()
				?.cloudFlareUserAgent = userAgent
		}.onFailure {
			it.printStackTraceDebug("CloudFlareActivity::rememberAlignedUserAgent")
		}
	}

	private fun shouldUseInterception(source: MangaSource, repository: ParserMangaRepository?): Boolean {
		Log.d(TAG, "shouldUseInterception called for source: ${source.name}")
		Log.d(TAG, "Repository type: ${repository?.javaClass?.simpleName}")

		if (repository !is ParserMangaRepository) {
			Log.d(TAG, "Repository is not ParserMangaRepository, returning false")
			return false
		}

		// Check if parser has InterceptCloudflare ConfigKey
		val configKeys = repository.getConfigKeys()
		Log.d(TAG, "Config keys count: ${configKeys.size}")
		Log.d(TAG, "Config keys: ${configKeys.map { it.javaClass.simpleName }}")

		val interceptKey = configKeys.filterIsInstance<ConfigKey.InterceptCloudflare>().firstOrNull()
		Log.d(TAG, "InterceptCloudflare key found: ${interceptKey != null}")
		if (interceptKey != null) {
			Log.d(TAG, "InterceptCloudflare defaultValue: ${interceptKey.defaultValue}")
		}

		val result = interceptKey?.defaultValue == true
		Log.d(TAG, "Returning: $result")
		return result
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
			return resultCode == RESULT_OK
		}
	}

	companion object {

		const val TAG = "CloudFlareActivity"
		const val EXTRA_AUTO_RESOLVE = "auto_resolve"
		const val EXTRA_SESSION_ID = "auto_resolve_session"
		private const val EXTRA_AUTO_RECREATE_COUNT = "auto_recreate_count"
		private const val VIEWPORT_POLL_INTERVAL_MS = 16L
		private const val VIEWPORT_FOCUS_TIMEOUT_MS = 2_000L
		private const val RESOLVE_POLL_INTERVAL_MS = 800L
		private const val AUTO_RETRY_DELAY_MS = 6_000L
		private const val MANUAL_FALLBACK_DELAY_MS = 15_000L
		private const val CLEARANCE_RECREATE_GRACE_MS = 500L
		private const val REQUIRED_STABLE_PASSES = 3
		private const val HIDDEN_RECREATED_STABLE_PASSES = 1
		private const val MAX_AUTO_RECREATE_COUNT = 1
		private const val CLEARANCE_COOKIE_NAME = "cf_clearance"
		private const val HIDDEN_RENDER_ALPHA = 0.01f
		private const val CF_STATE_OK = "ok"
		private const val CF_STATE_WAIT = "wait"
		private const val ANDROID_UA_TOKEN = "Android"
	}
}
