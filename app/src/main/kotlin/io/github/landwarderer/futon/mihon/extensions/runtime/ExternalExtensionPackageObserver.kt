package io.github.landwarderer.futon.mihon.extensions.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

fun registerExternalExtensionPackageObserver(
	context: Context,
	onPackageChanged: suspend () -> Unit,
): BroadcastReceiver {
	val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val pendingResult = goAsync()
			io.github.landwarderer.futon.core.util.ext.processLifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
				try {
					onPackageChanged()
				} finally {
					pendingResult?.finish()
				}
			}
		}
	}
	ContextCompat.registerReceiver(
		context,
		receiver,
		IntentFilter().apply {
			addAction(Intent.ACTION_PACKAGE_ADDED)
			addAction(Intent.ACTION_PACKAGE_REPLACED)
			addAction(Intent.ACTION_PACKAGE_REMOVED)
			addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
			addDataScheme("package")
		},
		ContextCompat.RECEIVER_EXPORTED,
	)
	return receiver
}
