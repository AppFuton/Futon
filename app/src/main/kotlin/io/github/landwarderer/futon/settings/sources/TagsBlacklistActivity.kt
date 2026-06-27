package io.github.landwarderer.futon.settings.sources

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.widgets.ChipsView
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ActivityTagsBlacklistBinding
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint
class TagsBlacklistActivity : BaseActivity<ActivityTagsBlacklistBinding>(), ChipsView.OnChipClickListener {

	private val viewModel by viewModels<TagsBlacklistViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityTagsBlacklistBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setTitle(R.string.tags_blacklist)

		viewBinding.chipsBlacklisted.onChipClickListener = this
		viewBinding.chipsAvailable.onChipClickListener = this

		combine(viewModel.allTags, viewModel.blacklistedTags, ::Pair).observe(this) { (all, blacklisted) ->
			updateChips(all, blacklisted)
		}

		viewModel.isLoading.observe(this) { hasLoaded ->
            if (hasLoaded) {
                viewBinding.progressBar.visibility = View.GONE
            } else {
                viewBinding.progressBar.visibility = View.VISIBLE
            }
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.scrollView.updatePadding(
			left = bars.left,
			right = bars.right,
			bottom = bars.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = bars.left,
			right = bars.right,
			top = bars.top,
		)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	private fun updateChips(all: List<String>, blacklisted: Set<String>) {
		val blacklistedChips = blacklisted.sorted().map { tag ->
			ChipsView.ChipModel(title = tag, data = tag, isChecked = true)
		}
		viewBinding.chipsBlacklisted.setChips(blacklistedChips)
		viewBinding.textBlacklistedTitle.isVisible = blacklistedChips.isNotEmpty()
		viewBinding.chipsBlacklisted.isVisible = blacklistedChips.isNotEmpty()

		val availableTags = all.filter { it !in blacklisted }
		val availableChips = availableTags.map { tag ->
			ChipsView.ChipModel(title = tag, data = tag, isChecked = false)
		}
		viewBinding.chipsAvailable.setChips(availableChips)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		val tag = data as? String ?: return
		viewModel.toggleTag(tag)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_search, menu)
		val searchItem = menu.findItem(R.id.action_search)
		val searchView = searchItem.actionView as SearchView
		searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String?): Boolean {
				viewModel.performSearch(query)
				return true
			}

			override fun onQueryTextChange(newText: String?): Boolean {
				viewModel.performSearch(newText)
				return true
			}
		})
		return true
	}
}
