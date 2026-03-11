package io.github.landwarderer.futon.main.ui.consent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.sheet.BaseAdaptiveSheet
import io.github.landwarderer.futon.core.util.ext.consume
import io.github.landwarderer.futon.databinding.SheetCrashAnalyticsConsentBinding
import javax.inject.Inject

@AndroidEntryPoint
class CrashAnalyticsConsentSheet : BaseAdaptiveSheet<SheetCrashAnalyticsConsentBinding>(), View.OnClickListener {

	@Inject
	lateinit var settings: AppSettings

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): SheetCrashAnalyticsConsentBinding {
		return SheetCrashAnalyticsConsentBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetCrashAnalyticsConsentBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonAgree.setOnClickListener(this)
		binding.buttonCancel.setOnClickListener(this)
		binding.textViewLearnMore.setOnClickListener(this)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_agree -> {
				settings.isCrashAnalyticsEnabled = true
				dismissAllowingStateLoss()
			}

			R.id.button_cancel -> {
				settings.isCrashAnalyticsEnabled = false
				dismissAllowingStateLoss()
			}

			R.id.textView_learn_more -> {
				startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://futonapp.pages.dev/privacy/data-collected/")))
			}
		}
	}
}
