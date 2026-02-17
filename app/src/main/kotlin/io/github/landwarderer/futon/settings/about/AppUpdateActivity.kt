package io.github.landwarderer.futon.settings.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.core.text.buildSpannedString
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.github.AppVersion
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.util.ext.consumeAllSystemBarsInsets
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.setTextAndVisible
import io.github.landwarderer.futon.core.util.ext.systemBarsInsets
import io.github.landwarderer.futon.databinding.ActivityAppUpdateBinding

@AndroidEntryPoint
class AppUpdateActivity : BaseActivity<ActivityAppUpdateBinding>(), View.OnClickListener {

	private val viewModel: AppUpdateViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityAppUpdateBinding.inflate(layoutInflater))
		viewModel.nextVersion.observe(this, ::onNextVersionChanged)
		viewBinding.buttonCancel.setOnClickListener(this)
		viewBinding.buttonUpdate.setOnClickListener(this)
		
		viewModel.isLoading.observe(this) { isLoading ->
			viewBinding.buttonUpdate.isEnabled = viewModel.nextVersion.value != null && !isLoading
		}
		viewModel.onError.observeEvent(this, ::onError)
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.root.updatePadding(top = barsInsets.top)
		viewBinding.dockedToolbarChild.updateLayoutParams<MarginLayoutParams> {
			leftMargin = barsInsets.left
			rightMargin = barsInsets.right
			bottomMargin = barsInsets.bottom
		}
		viewBinding.scrollView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_cancel -> finishAfterTransition()
			R.id.button_update -> openGitHub()
		}
	}

	private suspend fun onNextVersionChanged(version: AppVersion?) {
		viewBinding.buttonUpdate.isEnabled = version != null && !viewModel.isLoading.value
		if (version == null) {
			viewBinding.textViewContent.setText(R.string.loading_)
			return
		}
		val markwon = Markwon.create(this)
		val message = withContext(Dispatchers.IO) {
			buildSpannedString {
				append(getString(R.string.new_version_s, version.name))
				appendLine()
				appendLine()
				append(markwon.toMarkdown(version.description))
				appendLine()
				appendLine()
				append(getString(R.string.github_download_warning))
			}
		}
		markwon.setParsedMarkdown(viewBinding.textViewContent, message)
	}

	private fun openGitHub() {
		val latestVersion = viewModel.nextVersion.value ?: return
		if (!router.openExternalBrowser(latestVersion.url, getString(R.string.open_in_browser))) {
			Snackbar.make(viewBinding.scrollView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun onError(e: Throwable) {
		viewBinding.textViewError.setTextAndVisible(R.string.error_occurred)
	}
}
