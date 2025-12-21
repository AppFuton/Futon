package io.github.landwarderer.futon.settings.storage

import androidx.annotation.StringRes
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback
import io.github.landwarderer.futon.list.ui.model.ListModel
import java.io.File

data class DirectoryModel(
	val title: String?,
	@StringRes val titleRes: Int,
	val file: File?,
	val isRemovable: Boolean,
	val isChecked: Boolean,
	val isAvailable: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is DirectoryModel && other.file == file && other.title == title && other.titleRes == titleRes
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is DirectoryModel && previousState.isChecked != isChecked) {
			ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}
