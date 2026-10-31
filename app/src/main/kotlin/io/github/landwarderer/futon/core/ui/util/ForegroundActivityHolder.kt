package io.github.landwarderer.futon.core.ui.util

import android.app.Activity
import io.github.landwarderer.futon.core.ui.DefaultActivityLifecycleCallbacks
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the currently resumed Activity, so the automatic CloudFlare solver can be started on top of
 * the screen the user is looking at instead of in a new task.
 */
@Singleton
class ForegroundActivityHolder @Inject constructor() : DefaultActivityLifecycleCallbacks {

	@Volatile
	private var activityRef: WeakReference<Activity>? = null

	val current: Activity?
		get() = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

	override fun onActivityResumed(activity: Activity) {
		activityRef = WeakReference(activity)
	}

	override fun onActivityPaused(activity: Activity) {
		if (activityRef?.get() === activity) {
			activityRef = null
		}
	}
}
