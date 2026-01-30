package io.github.landwarderer.futon.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import io.github.landwarderer.futon.core.db.entity.TachiyomiExtensionEntity

@Dao
abstract class TachiyomiExtensionsDao {

	@Query("SELECT * FROM tachiyomi_extensions ORDER BY name ASC")
	abstract suspend fun findAll(): List<TachiyomiExtensionEntity>

	@Query("SELECT * FROM tachiyomi_extensions WHERE is_enabled = 1 ORDER BY name ASC")
	abstract suspend fun findAllEnabled(): List<TachiyomiExtensionEntity>

	@Query("SELECT * FROM tachiyomi_extensions WHERE pkg_name = :pkgName")
	abstract suspend fun find(pkgName: String): TachiyomiExtensionEntity?

	@Query("SELECT * FROM tachiyomi_extensions ORDER BY name ASC")
	abstract fun observeAll(): Flow<List<TachiyomiExtensionEntity>>

	@Query("SELECT * FROM tachiyomi_extensions WHERE is_enabled = 1 ORDER BY name ASC")
	abstract fun observeEnabled(): Flow<List<TachiyomiExtensionEntity>>

	@Query("SELECT is_enabled FROM tachiyomi_extensions WHERE pkg_name = :pkgName")
	abstract fun observeIsEnabled(pkgName: String): Flow<Boolean>

	@Query("UPDATE tachiyomi_extensions SET is_enabled = :isEnabled WHERE pkg_name = :pkgName")
	abstract suspend fun setEnabled(pkgName: String, isEnabled: Boolean)

	@Query("UPDATE tachiyomi_extensions SET is_enabled = :isEnabled")
	abstract suspend fun setAllEnabled(isEnabled: Boolean)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	abstract suspend fun insert(entity: TachiyomiExtensionEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	abstract suspend fun insertAll(entities: List<TachiyomiExtensionEntity>)

	@Upsert
	abstract suspend fun upsert(entity: TachiyomiExtensionEntity)

	@Query("DELETE FROM tachiyomi_extensions WHERE pkg_name = :pkgName")
	abstract suspend fun delete(pkgName: String)

	@Query("DELETE FROM tachiyomi_extensions WHERE pkg_name IN (:pkgNames)")
	abstract suspend fun deleteAll(pkgNames: List<String>)

	@Query("DELETE FROM tachiyomi_extensions")
	abstract suspend fun deleteAll()

	@Transaction
	open suspend fun sync(installedExtensions: List<TachiyomiExtensionEntity>) {
		val installedPkgNames = installedExtensions.map { it.pkgName }.toSet()
		val existingExtensions = findAll()
		val existingPkgNames = existingExtensions.map { it.pkgName }.toSet()

		val toDelete = existingPkgNames - installedPkgNames
		if (toDelete.isNotEmpty()) {
			deleteAll(toDelete.toList())
		}

		val toInsertOrUpdate = installedExtensions.map { newExt ->
			val existing = existingExtensions.find { it.pkgName == newExt.pkgName }
			if (existing != null) {
				newExt.copy(isEnabled = existing.isEnabled)
			} else {
				newExt.copy(isEnabled = true)
			}
		}

		insertAll(toInsertOrUpdate)
	}
}
