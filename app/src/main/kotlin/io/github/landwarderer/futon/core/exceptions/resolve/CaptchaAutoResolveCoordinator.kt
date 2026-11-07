package io.github.landwarderer.futon.core.exceptions.resolve

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.browser.cloudflare.CloudFlareActivity
import io.github.landwarderer.futon.browser.cloudflare.CloudFlareHiddenActivity
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.model.UnknownMangaSource
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.prefs.SourceSettings
import io.github.landwarderer.futon.core.ui.DefaultActivityLifecycleCallbacks
import io.github.landwarderer.futon.core.ui.util.ForegroundActivityHolder
import io.github.landwarderer.futon.core.util.ext.findCloudFlareException
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single automatic Cloudflare verification session allowed in the app process.
 *
 * A session is a [CloudFlareHiddenActivity] started on top of the foreground screen. Sessions are only
 * started for explicit user actions (opening a catalog, a manga or a chapter); background work may wait
 * for a running session but never starts one.
 */
@Singleton
class CaptchaAutoResolveCoordinator @Inject constructor(
	@ApplicationContext private val context: Context,
	private val foregroundActivityHolder: ForegroundActivityHolder,
) : DefaultActivityLifecycleCallbacks, DefaultLifecycleObserver {

	private val stateMutex = Mutex()
	private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
	private val recentSuccessAt = ConcurrentHashMap<MangaSource, Long>()
	private val recentFailureAt = ConcurrentHashMap<MangaSource, Long>()
	private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
	private val reorderPending = AtomicBoolean(false)
	private val isProcessObserverRegistered = AtomicBoolean(false)
	private val sessionIdCounter = AtomicLong(0L)

	@Volatile
	private var activeSession: ResolveSession? = null

	@Volatile
	private var hiddenActivityRef: WeakReference<CloudFlareHiddenActivity>? = null

	fun registerHiddenActivity(activity: CloudFlareHiddenActivity, sessionId: Long) {
		val session = activeSession?.takeIf { it.id == sessionId }
		if (session == null) {
			// A solver of a session which has already ended (e.g. restored by the system): it must not linger.
			activity.finish()
			return
		}
		hiddenActivityRef = WeakReference(activity)
		reorderPending.set(false)
		session.launched.complete(Unit)
	}

	fun unregisterHiddenActivity(activity: CloudFlareHiddenActivity) {
		if (hiddenActivityRef?.get() === activity) {
			hiddenActivityRef = null
		}
	}

	/** Called by the solver Activity of session [sessionId] when it finishes or is cancelled. */
	fun notifyResolveResult(sessionId: Long, success: Boolean) {
		activeSession
			?.takeIf { it.id == sessionId }
			?.activityResult
			?.complete(success)
	}

	fun isResolveActive(): Boolean = activeSession != null

	/** Waits for whichever session is active. This method never starts verification. */
	suspend fun awaitActiveResolve(): Boolean? = activeSession?.result?.await()

	/**
	 * Runs an operation in sync with the solver. Background and prefetch callers pass
	 * [mayStartVerification] as false: they may wait for an existing session but never create one.
	 */
	suspend fun <T> runWithVerification(
		mayStartVerification: Boolean,
		block: suspend () -> T,
	): T {
		activeSession?.result?.await()
		var didVerify = false
		while (true) {
			try {
				return block()
			} catch (e: Exception) {
				val cf = e.findCloudFlareException()
				if (cf !is CloudFlareProtectedException || !mayStartVerification) throw e
				if (didVerify) {
					// Verification reported success and this request is *still* Cloudflare-protected.
					// Another identical session cannot fix that, so stop asking for one.
					notifyVerificationIneffective(cf.source)
					throw e
				}
				if (!resolveIfEnabled(cf)) throw e
				didVerify = true
			}
		}
	}

	/**
	 * Reports that verification which finished successfully did not actually unblock [source]
	 * (e.g. Cloudflare does not honour the clearance for OkHttp). Suppresses automatic solving for a
	 * while so the app does not re-solve the same challenge forever.
	 */
	fun notifyVerificationIneffective(source: MangaSource) {
		recentSuccessAt.remove(source)
		recentFailureAt[source] = System.currentTimeMillis()
	}

	/** Resolves [exception] only when automatic solving is enabled for its source. */
	suspend fun resolveIfEnabled(exception: CloudFlareProtectedException): Boolean {
		val source = exception.source
		if (source == UnknownMangaSource) return false
		if (SourceSettings(context, source).isCaptchaAutoResolveDisabled) return false
		val now = System.currentTimeMillis()
		val lastSuccess = recentSuccessAt[source]
		if (lastSuccess != null && now - lastSuccess < RECENT_SUCCESS_COOLDOWN_MS) {
			// Requests that started before the recent session finished: just let them retry.
			return true
		}
		// A source whose challenge just failed must not spawn a solver for every following request.
		// Report "not resolved" instead so the caller falls back to the ordinary manual "Solve".
		val lastFailure = recentFailureAt[source]
		if (lastFailure != null && now - lastFailure < RECENT_FAILURE_COOLDOWN_MS) {
			return false
		}
		return resolve(exception)
	}

	/** Joins the current session for the same source or atomically creates the only allowed session. */
	private suspend fun resolve(exception: CloudFlareProtectedException): Boolean {
		val source = exception.source
		while (true) {
			val claim = stateMutex.withLock {
				activeSession?.let { return@withLock SessionClaim(it, isOwner = false) }
				if (foregroundActivityHolder.current == null) {
					// The app is in background: never start a solver from there.
					return false
				}
				val session = ResolveSession(
					id = sessionIdCounter.incrementAndGet(),
					source = source,
					exception = exception,
				)
				activeSession = session
				SessionClaim(session, isOwner = true)
			}
			if (claim.isOwner) {
				showSolvingToast()
				scope.launch { runSession(claim.session) }
			}
			val result = claim.session.result.await()
			if (claim.session.source == source) {
				return result
			}
			// That session was solving another source, ours is still protected: try to claim the next one.
		}
	}

	private suspend fun runSession(session: ResolveSession) {
		var success = false
		try {
			if (launch(session)) {
				val launched = withTimeoutOrNull(LAUNCH_TIMEOUT_MS) { session.launched.await() }
				if (launched != null) {
					success = session.activityResult.await()
				}
			}
		} catch (e: Throwable) {
			e.printStackTraceDebug("CaptchaAutoResolveCoordinator::runSession")
		} finally {
			if (success) {
				recentSuccessAt[session.source] = System.currentTimeMillis()
				recentFailureAt.remove(session.source)
			} else if (!session.isCancelledByBackground) {
				// Backgrounding is the user's choice, not a verdict on the challenge, so it must not
				// arm the cooldown.
				recentFailureAt[session.source] = System.currentTimeMillis()
			}
			stateMutex.withLock {
				if (activeSession === session) activeSession = null
			}
			hiddenActivityRef?.get()?.takeIf { it.sessionId == session.id }?.cancelAutomaticResolve()
			hiddenActivityRef = null
			reorderPending.set(false)
			// Clear ownership before releasing waiters, so they can claim the next session.
			session.result.complete(success)
		}
	}

	private fun launch(session: ResolveSession): Boolean {
		val launcher = foregroundActivityHolder.current ?: return false
		val intent = AppRouter.cloudFlareResolveIntent(launcher, session.exception, hidden = true).apply {
			putExtra(CloudFlareActivity.EXTRA_AUTO_RESOLVE, true)
			putExtra(CloudFlareActivity.EXTRA_SESSION_ID, session.id)
		}
		return runCatching {
			launcher.startActivity(intent)
		}.onFailure {
			it.printStackTraceDebug("CaptchaAutoResolveCoordinator::launch")
		}.isSuccess
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		// Registered lazily, so that no session can ever outlive the app going to background.
		if (isProcessObserverRegistered.compareAndSet(false, true)) {
			ProcessLifecycleOwner.get().lifecycle.addObserver(this)
		}
	}

	/** Keep the existing hidden WebView above newly opened app screens without constructing another one. */
	override fun onActivityResumed(activity: Activity) {
		if (activity is CloudFlareActivity) {
			reorderPending.set(false)
			return
		}
		val solver = hiddenActivityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
		if (solver.taskId != activity.taskId) return // reordering cannot reach another task, it would create a copy
		if (!solver.shouldStayHiddenAndFocused() || !reorderPending.compareAndSet(false, true)) return
		mainHandler.post {
			if (!solver.shouldStayHiddenAndFocused() || activity.isFinishing || activity.isDestroyed) {
				reorderPending.set(false)
				return@post
			}
			activity.startActivity(
				Intent(activity, CloudFlareHiddenActivity::class.java).addFlags(
					Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
				),
			)
		}
	}

	/** App backgrounding cancels the session and therefore releases every waiter with failure. */
	override fun onStop(owner: LifecycleOwner) {
		val session = activeSession ?: return
		session.isCancelledByBackground = true
		session.activityResult.complete(false)
	}

	private fun showSolvingToast() {
		mainHandler.post {
			Toast.makeText(context, R.string.captcha_solving, Toast.LENGTH_LONG).show()
		}
	}

	private class ResolveSession(
		val id: Long,
		val source: MangaSource,
		val exception: CloudFlareProtectedException,
	) {
		val launched = CompletableDeferred<Unit>()
		val activityResult = CompletableDeferred<Boolean>()
		val result = CompletableDeferred<Boolean>()

		@Volatile
		var isCancelledByBackground = false
	}

	private class SessionClaim(
		val session: ResolveSession,
		val isOwner: Boolean,
	)

	private companion object {

		const val RECENT_SUCCESS_COOLDOWN_MS = 30_000L
		const val RECENT_FAILURE_COOLDOWN_MS = 3 * 60_000L
		const val LAUNCH_TIMEOUT_MS = 10_000L
	}
}
