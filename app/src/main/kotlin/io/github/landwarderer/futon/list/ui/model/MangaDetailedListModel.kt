package io.github.landwarderer.futon.list.ui.model

import io.github.landwarderer.futon.core.ui.model.MangaOverride
import io.github.landwarderer.futon.core.ui.widgets.ChipsView
import io.github.landwarderer.futon.list.domain.ReadingProgress
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback.Companion.PAYLOAD_ANYTHING_CHANGED
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import org.koitharu.kotatsu.parsers.model.Manga

data class MangaDetailedListModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	val subtitle: String?,
	override val counter: Int,
	val progress: ReadingProgress?,
	val isFavorite: Boolean,
	val isSaved: Boolean,
	val tags: List<ChipsView.ChipModel>,
) : MangaListModel() {

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is MangaDetailedListModel || previousState.manga != manga -> null

		previousState.progress != progress -> PAYLOAD_PROGRESS_CHANGED
		previousState.isFavorite != isFavorite ||
			previousState.isSaved != isSaved -> PAYLOAD_ANYTHING_CHANGED

		else -> super.getChangePayload(previousState)
	}
}
