package io.github.landwarderer.futon.explore.ui.model

import io.github.landwarderer.futon.core.model.MangaSourceInfo
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback
import io.github.landwarderer.futon.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source.name == source.name
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is MangaSourceItem && previousState.source.name == source.name) {
			ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}

