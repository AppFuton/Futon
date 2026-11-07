package io.github.landwarderer.futon.explore.ui.preset

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.getSourceLanguageDisplayName
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.util.DefaultTextWatcher
import io.github.landwarderer.futon.core.util.ext.consumeAllSystemBarsInsets
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.systemBarsInsets
import io.github.landwarderer.futon.databinding.ActivityPresetEditBinding
import io.github.landwarderer.futon.explore.data.SourcePreset

@AndroidEntryPoint
class SourcePresetEditActivity :
	BaseActivity<ActivityPresetEditBinding>(),
	View.OnClickListener,
	DefaultTextWatcher {

	private val viewModel by viewModels<SourcePresetEditViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityPresetEditBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		viewBinding.buttonDone.setOnClickListener(this)
		viewBinding.editName.addTextChangedListener(this)
		afterTextChanged(viewBinding.editName.text)

		initLanguageChips()

		viewModel.onSaved.observeEvent(this) { finishAfterTransition() }
		viewModel.preset.observe(this, ::onPresetChanged)
		viewModel.isLoading.observe(this, ::onLoadingStateChanged)
		viewModel.onError.observeEvent(this, ::onError)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.root.setPadding(
			barsInsets.left,
			barsInsets.top,
			barsInsets.right,
			barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_done -> viewModel.save(
				title = viewBinding.editName.text?.toString()?.trim().orEmpty(),
				selectedLanguages = getSelectedLanguages(),
			)
		}
	}

	override fun afterTextChanged(s: Editable?) {
		viewBinding.buttonDone.isEnabled = !s.isNullOrBlank() && !viewModel.isLoading.value
	}

	private fun initLanguageChips() {
		val chipGroup = viewBinding.chipGroupLanguages
		chipGroup.removeAllViews()
		val locales = viewModel.allLocales
			.map { it to getSourceLanguageDisplayName(this, it) }
			.sortedBy { it.second }
		for ((locale, title) in locales) {
			val chip = Chip(this).apply {
				text = title
				isCheckable = true
				tag = locale
			}
			chipGroup.addView(chip)
		}
	}

	private fun onPresetChanged(preset: SourcePreset?) {
		setTitle(if (preset == null) R.string.create_preset else R.string.edit_preset)
		if (preset == null) return
		if (viewBinding.editName.text.isNullOrEmpty()) {
			viewBinding.editName.setText(preset.title)
		}
		val chipGroup = viewBinding.chipGroupLanguages
		for (i in 0 until chipGroup.childCount) {
			val chip = chipGroup.getChildAt(i) as? Chip ?: continue
			chip.isChecked = chip.tag as? String in preset.languages
		}
	}

	private fun getSelectedLanguages(): Set<String> {
		val chipGroup = viewBinding.chipGroupLanguages
		val result = LinkedHashSet<String>()
		for (i in 0 until chipGroup.childCount) {
			val chip = chipGroup.getChildAt(i) as? Chip ?: continue
			if (chip.isChecked) {
				(chip.tag as? String)?.let { result.add(it) }
			}
		}
		// Keep languages whose sources are not installed right now, so editing a preset does not drop them.
		viewModel.preset.value?.languages?.filterTo(result) { it !in viewModel.allLocales }
		return result
	}

	private fun onError(e: Throwable) {
		viewBinding.textViewError.text = e.getDisplayMessage(resources)
		viewBinding.textViewError.isVisible = true
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.buttonDone.isEnabled = !isLoading && !viewBinding.editName.text.isNullOrBlank()
		viewBinding.editName.isEnabled = !isLoading
		if (isLoading) {
			viewBinding.textViewError.isVisible = false
		}
	}

	companion object {

		fun newIntent(context: Context, presetId: Long = SourcePresetEditViewModel.NO_ID): Intent {
			return Intent(context, SourcePresetEditActivity::class.java)
				.putExtra(AppRouter.KEY_ID, presetId)
		}
	}
}
