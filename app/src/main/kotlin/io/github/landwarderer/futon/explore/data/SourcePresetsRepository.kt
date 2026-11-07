package io.github.landwarderer.futon.explore.data

import dagger.Reusable
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.util.ext.mapItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Reusable
class SourcePresetsRepository @Inject constructor(
	private val db: MangaDatabase,
) {

	private val dao: SourcePresetsDao
		get() = db.getSourcePresetsDao()

	fun observeAll(): Flow<List<SourcePreset>> {
		return dao.observeAll().mapItems { it.toSourcePreset() }
	}

	fun observe(id: Long): Flow<SourcePreset?> {
		return dao.observe(id).map { it?.toSourcePreset() }
	}

	suspend fun getAll(): List<SourcePreset> {
		return dao.findAll().map { it.toSourcePreset() }
	}

	suspend fun getById(id: Long): SourcePreset? {
		if (id == NO_PRESET) {
			return null
		}
		return dao.find(id)?.toSourcePreset()
	}

	suspend fun createPreset(title: String, languages: Set<String>, sources: Set<String>): SourcePreset {
		val entity = SourcePresetEntity(
			presetId = 0,
			title = title,
			languages = languages.joinPresetValues(),
			sources = sources.joinPresetValues(),
			createdAt = System.currentTimeMillis(),
			sortKey = dao.getNextSortKey(),
			deletedAt = 0L,
		)
		val id = dao.insert(entity)
		return entity.copy(presetId = id).toSourcePreset()
	}

	suspend fun updatePreset(id: Long, title: String, languages: Set<String>) {
		dao.update(id, title, languages.joinPresetValues())
	}

	suspend fun updatePresetSources(id: Long, sources: Set<String>) {
		dao.updateSources(id, sources.joinPresetValues())
	}

	suspend fun deletePreset(id: Long) {
		dao.delete(id)
	}

	companion object {

		const val NO_PRESET = 0L
	}
}
