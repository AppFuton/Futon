package io.github.landwarderer.futon.settings.utils

import io.github.landwarderer.futon.core.db.MangaDatabase
import javax.inject.Inject

class TagsAutoCompleteProvider @Inject constructor(
	private val db: MangaDatabase,
) : MultiAutoCompleteTextViewPreference.AutoCompleteProvider {

	override suspend fun getSuggestions(query: String): List<String> {
		if (query.isEmpty()) {
			return emptyList()
		}
		val tags = db.getTagsDao().searchAllTags(query = "$query%", limit = 10)
		val set = HashSet<String>()
		val result = ArrayList<String>(tags.size)
		for (tag in tags) {
			if (set.add(tag.title)) {
				result.add(tag.title)
			}
		}
		return result
	}
}
