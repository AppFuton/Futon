package io.github.landwarderer.futon.settings.userdata.storage

data class StorageUsage(
	val savedManga: Item,
	val pagesCache: Item,
	val otherCache: Item,
	val available: Item,
) {
	data class Item(
		val bytes: Long,
		val percent: Float,
	)
}
