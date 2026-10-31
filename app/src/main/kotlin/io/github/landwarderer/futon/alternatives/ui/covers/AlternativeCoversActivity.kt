package io.github.landwarderer.futon.alternatives.ui.covers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.MangaSource
import io.github.landwarderer.futon.core.model.getTitle
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseActivity
import io.github.landwarderer.futon.core.ui.BaseListAdapter
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.futon.core.util.ext.consumeAll
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.databinding.ActivityAlternativeCoversBinding
import io.github.landwarderer.futon.list.ui.adapter.ListItemType
import io.github.landwarderer.futon.list.ui.model.ListModel
import kotlinx.coroutines.flow.combine
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Covers for the same title from other sources, to be compared against the current one and picked.
 */
@AndroidEntryPoint
class AlternativeCoversActivity :
	BaseActivity<ActivityAlternativeCoversBinding>(),
	OnListItemClickListener<AlternativeCoverModel> {

	private val viewModel by viewModels<AlternativeCoversViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityAlternativeCoversBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		title = getString(R.string.alternative_covers)

		val adapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.MANGA_GRID, alternativeCoverAD(this))
		viewBinding.recyclerView.adapter = adapter
		viewBinding.buttonApply.setOnClickListener { applySelection() }

		viewModel.covers.observe(this) { covers ->
			adapter.items = covers
		}
		combine(viewModel.covers, viewModel.selectedUrl, ::Pair).observe(this) { (covers, url) ->
			val selected = covers.firstOrNull { it.coverUrl == url }
			viewBinding.imageViewPreview.setImageAsync(url, selected?.manga ?: viewModel.manga)
			viewBinding.textViewStatus.text = when {
				selected == null -> null
				selected.isCurrent -> getString(R.string.current_cover)
				else -> selected.source.getTitle(this)
			}
			// Re-picking the cover already in use would be a no-op write.
			viewBinding.buttonApply.isEnabled = selected != null && !selected.isCurrent
		}
		combine(viewModel.covers, viewModel.isRunning, ::Pair).observe(this) { (covers, isRunning) ->
			viewBinding.progress.isVisible = isRunning
			// Count only alternatives: the current cover leads the list, but is absent entirely when the
			// manga has no cover of its own.
			val showEmpty = covers.none { !it.isCurrent } && !isRunning
			viewBinding.textViewEmpty.isVisible = showEmpty
			viewBinding.recyclerView.isVisible = !showEmpty
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding.root.updatePadding(
			left = barsInsets.left,
			top = barsInsets.top,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAll(typeMask)
	}

	/** Tapping the carousel only previews; applying is a deliberate second step. */
	override fun onItemClick(item: AlternativeCoverModel, view: View) {
		viewModel.select(item.coverUrl)
	}

	private fun applySelection() {
		val url = viewModel.selectedUrl.value ?: return
		val selected = viewModel.covers.value.firstOrNull { it.coverUrl == url } ?: return
		// Whatever is still in flight is no longer worth fetching.
		viewModel.cancelSearch()
		setResult(
			RESULT_OK,
			Intent()
				.putExtra(EXTRA_COVER_URL, url)
				.putExtra(EXTRA_SOURCE, selected.source.name),
		)
		finish()
	}

	/** A cover picked from another source, with the source it came from (for request headers). */
	data class PickedCover(
		val url: String,
		val source: MangaSource,
	)

	class Contract : ActivityResultContract<Manga, PickedCover?>() {

		override fun createIntent(context: Context, input: Manga) =
			Intent(context, AlternativeCoversActivity::class.java)
				.putExtra(AppRouter.KEY_MANGA, ParcelableManga(input, withDescription = false))

		override fun parseResult(resultCode: Int, intent: Intent?): PickedCover? {
			if (resultCode != RESULT_OK || intent == null) {
				return null
			}
			val url = intent.getStringExtra(EXTRA_COVER_URL) ?: return null
			return PickedCover(url, MangaSource(intent.getStringExtra(EXTRA_SOURCE)))
		}
	}

	private companion object {

		const val EXTRA_COVER_URL = "cover_url"
		const val EXTRA_SOURCE = "cover_source"
	}
}
