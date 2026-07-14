package io.github.landwarderer.futon.download.ui.list.chapters

import io.github.landwarderer.futon.list.ui.ListModelDiffCallback
import io.github.landwarderer.futon.list.ui.model.ListModel

data class DownloadChapter(
	val id: Long,
	val number: String?,
	val name: String,
	val isDownloaded: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is DownloadChapter && other.id == id
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is DownloadChapter && previousState.id == id && previousState.number == number) {
			ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}
