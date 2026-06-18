package io.github.landwarderer.futon.core.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.ui.AlertDialogFragment
import io.github.landwarderer.futon.core.util.ext.copyToClipboard
import io.github.landwarderer.futon.core.util.ext.getCauseUrl
import io.github.landwarderer.futon.core.util.ext.isHttpUrl
import io.github.landwarderer.futon.core.util.ext.isReportable
import io.github.landwarderer.futon.core.util.ext.report
import io.github.landwarderer.futon.core.util.ext.requireSerializable
import io.github.landwarderer.futon.core.util.ext.setTextAndVisible
import io.github.landwarderer.futon.databinding.DialogErrorDetailsBinding

@AndroidEntryPoint
class ErrorDetailsDialog : AlertDialogFragment<DialogErrorDetailsBinding>(), View.OnClickListener {

	private lateinit var exception: Throwable

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = requireArguments()
		exception = args.requireSerializable(AppRouter.KEY_ERROR)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogErrorDetailsBinding {
		return DialogErrorDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: DialogErrorDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonBrowser.setOnClickListener(this)
		binding.textViewSummary.text = exception.message
		val isUrlAvailable = exception.getCauseUrl()?.isHttpUrl() == true
		binding.buttonBrowser.isVisible = isUrlAvailable
		binding.textViewBrowser.isVisible = isUrlAvailable
		binding.textViewDescription.setTextAndVisible(
			if (exception.isReportable()) {
				R.string.error_disclaimer_report
			} else {
				0
			},
		)
	}

	@Suppress("NAME_SHADOWING")
	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		val builder = super.onBuildDialog(builder)
			.setCancelable(true)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error_details)
			.setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
				context?.copyToClipboard(getString(R.string.error), exception.stackTraceToString())
			}
		if (exception.isReportable()) {
			builder.setPositiveButton(R.string.report) { _, _ ->
				exception.report(silent = true)
				dismiss()
			}
		}
		return builder
	}

	override fun onClick(v: View) {
		router.openBrowser(
			url = exception.getCauseUrl() ?: return,
			source = null,
			title = null,
		)
	}
}
