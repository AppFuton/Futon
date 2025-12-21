package io.github.landwarderer.futon.backups.ui.restore

import androidx.annotation.StringRes
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.backups.domain.BackupSection
import io.github.landwarderer.futon.list.ui.ListModelDiffCallback
import io.github.landwarderer.futon.list.ui.model.ListModel

data class BackupSectionModel(
	val section: BackupSection,
	val isChecked: Boolean,
	val isEnabled: Boolean,
) : ListModel {

	@get:StringRes
	val titleResId: Int
		get() = when (section) {
			BackupSection.INDEX -> 0 // should not appear here
			BackupSection.HISTORY -> R.string.history
			BackupSection.CATEGORIES -> R.string.favourites_categories
			BackupSection.FAVOURITES -> R.string.favourites
			BackupSection.SETTINGS -> R.string.settings
			BackupSection.SETTINGS_READER_GRID -> R.string.reader_actions
			BackupSection.BOOKMARKS -> R.string.bookmarks
			BackupSection.SOURCES -> R.string.remote_sources
			BackupSection.SCROBBLING -> R.string.tracking
			BackupSection.STATS -> R.string.statistics
			BackupSection.SAVED_FILTERS -> R.string.saved_filters
		}

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is BackupSectionModel && other.section == section
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		if (previousState !is BackupSectionModel) {
			return null
		}
		return if (previousState.isEnabled != isEnabled) {
			ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		} else if (previousState.isChecked != isChecked) {
			ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}
