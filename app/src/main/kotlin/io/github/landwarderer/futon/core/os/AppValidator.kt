package io.github.landwarderer.futon.core.os

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import io.github.landwarderer.futon.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppValidator @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	@SuppressLint("InlinedApi")
	val isOriginalApp = suspendLazy(Dispatchers.IO) {
		val certificates = mapOf(CERT_SHA256.hexToByteArray() to PackageManager.CERT_INPUT_SHA256)
		PackageInfoCompat.hasSignatures(context.packageManager, context.packageName, certificates, false)
	}

	private companion object {
		private const val CERT_SHA256 = "ef48b22ef2c54045531f6e7600c27ec3d03b71221e0b05ffb68e3357cf8e4d40"
	}
}
