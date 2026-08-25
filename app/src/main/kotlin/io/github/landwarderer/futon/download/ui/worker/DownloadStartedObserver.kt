package io.github.landwarderer.futon.download.ui.worker

import android.view.View
import com.google.android.material.snackbar.Snackbar
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.util.ext.findActivity
import io.github.landwarderer.futon.main.ui.owners.BottomNavOwner
import kotlinx.coroutines.flow.FlowCollector

class DownloadStartedObserver(
	private val snackbarHost: View,
) : FlowCollector<Unit> {

	override suspend fun emit(value: Unit) {
		val snackbar = Snackbar.make(snackbarHost, R.string.download_started, Snackbar.LENGTH_LONG)
		(snackbarHost.context.findActivity() as? BottomNavOwner)?.let {
			snackbar.anchorView = it.bottomNavView
		}
		val router = AppRouter.from(snackbarHost)
		if (router != null) {
			snackbar.setAction(R.string.details) { router.openDownloads() }
		}
		snackbar.show()
	}
}
