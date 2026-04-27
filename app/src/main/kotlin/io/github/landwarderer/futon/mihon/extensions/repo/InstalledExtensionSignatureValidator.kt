package io.github.landwarderer.futon.mihon.extensions.repo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledExtensionSignatureValidator @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val cache = ConcurrentHashMap<String, Set<String>>()

	fun isTrusted(packageName: String, expectedFingerprint: String): Boolean {
		return ExtensionFingerprintTrust.isTrusted(expectedFingerprint, getFingerprints(packageName))
	}

	private fun getFingerprints(packageName: String): Set<String> {
		return cache.getOrPut(packageName) {
			runCatching {
				val packageInfo = context.packageManager.getPackageInfoCompat(packageName)
				getSignatures(packageInfo)
					.mapTo(LinkedHashSet()) { signature ->
						MessageDigest.getInstance("SHA-256")
							.digest(signature.toByteArray())
							.joinToString("") { byte -> "%02x".format(byte) }
					}
			}.getOrDefault(emptySet())
		}
	}

	private fun getSignatures(packageInfo: PackageInfo): Array<Signature> = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
			val signingInfo = packageInfo.signingInfo ?: return emptyArray()
			if (signingInfo.hasMultipleSigners()) {
				signingInfo.apkContentsSigners
			} else {
				signingInfo.signingCertificateHistory
			}
		}

		else -> {
			@Suppress("DEPRECATION")
			packageInfo.signatures ?: emptyArray()
		}
	}

	private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
		} else {
			@Suppress("DEPRECATION")
			getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
		}
	}
}
