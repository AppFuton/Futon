package io.github.landwarderer.futon.details.ui

import com.google.android.material.snackbar.Snackbar
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.exceptions.UnsupportedSourceException
import io.github.landwarderer.futon.core.exceptions.resolve.ErrorObserver
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.prefs.SourceSettings
import io.github.landwarderer.futon.core.util.ext.findCloudFlareException
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.core.util.ext.isNetworkError
import io.github.landwarderer.futon.core.util.ext.isSerializable
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.exception.ParseException

class DetailsErrorObserver(
	override val activity: DetailsActivity,
	private val viewModel: DetailsViewModel,
	private val resolver: ExceptionResolver?,
) : ErrorObserver(
	activity.viewBinding.scrollView, null, resolver,
	{ isResolved ->
		if (isResolved) {
			viewModel.reload()
		}
	},
) {

	private var isAutoResolveAttempted = false

	override suspend fun emit(value: Throwable) {
		// Opening a manga is an explicit user action, so a silent captcha solve is appropriate. Only once per
		// screen: if the source is still protected after that, the snackbar with the manual "Solve" follows.
		val cf = value.findCloudFlareException()
		if (
			cf is CloudFlareProtectedException &&
			resolver != null &&
			!isAutoResolveAttempted &&
			!SourceSettings(host.context, cf.source).isCaptchaAutoResolveDisabled
		) {
			isAutoResolveAttempted = true
			if (resolver.resolve(cf, tryAutoResolve = true)) {
				viewModel.reload()
				return
			}
		}
		val snackbar = Snackbar.make(host, value.getDisplayMessage(host.context.resources), Snackbar.LENGTH_SHORT)
		snackbar.setAnchorView(activity.viewBinding.containerBottomSheet)
		if (value is NotFoundException || value is UnsupportedSourceException) {
			snackbar.duration = Snackbar.LENGTH_INDEFINITE
		}
		when {
			canResolve(value) -> {
				snackbar.setAction(ExceptionResolver.getResolveStringId(value)) {
					resolve(value)
				}
			}

			value is ParseException -> {
				val router = router()
				if (router != null && value.isSerializable()) {
					snackbar.setAction(R.string.details) {
						router.showErrorDialog(value)
					}
				}
			}

			value.isNetworkError() -> {
				snackbar.setAction(R.string.try_again) {
					viewModel.reload()
				}
			}
		}
		snackbar.show()
	}
}
