package io.github.landwarderer.futon.explore.data

import android.os.Parcelable
import io.github.landwarderer.futon.list.ui.model.ListModel
import kotlinx.parcelize.Parcelize

@Parcelize
data class SourcePreset(
	val id: Long,
	val title: String,
	val languages: Set<String>,
	val sources: Set<String>,
	val createdAt: Long,
	val sortKey: Int,
) : Parcelable, ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is SourcePreset && id == other.id
	}
}

fun SourcePresetEntity.toSourcePreset() = SourcePreset(
	id = presetId,
	title = title,
	languages = languages.splitPresetValues(),
	sources = sources.splitPresetValues(),
	createdAt = createdAt,
	sortKey = sortKey,
)

internal fun Collection<String>.joinPresetValues(): String = asSequence()
	.map { it.trim() }
	.filter { it.isNotEmpty() }
	.distinct()
	.joinToString(",")

internal fun String.splitPresetValues(): Set<String> = if (isBlank()) {
	emptySet()
} else {
	splitToSequence(',')
		.map { it.trim() }
		.filterTo(LinkedHashSet()) { it.isNotEmpty() }
}
