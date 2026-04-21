package io.github.landwarderer.futon.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.landwarderer.futon.core.db.entity.ExternalExtensionRepoEntity
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalExtensionRepoDao {

    @Query("SELECT * FROM external_extension_repos WHERE type = :type")
    fun observeByType(type: ExternalExtensionType): Flow<List<ExternalExtensionRepoEntity>>

    @Query("SELECT * FROM external_extension_repos WHERE type = :type")
    suspend fun getByType(type: ExternalExtensionType): List<ExternalExtensionRepoEntity>

    @Query("SELECT * FROM external_extension_repos WHERE type = :type AND baseUrl = :baseUrl LIMIT 1")
    suspend fun get(type: ExternalExtensionType, baseUrl: String): ExternalExtensionRepoEntity?

    @Query("SELECT * FROM external_extension_repos WHERE type = :type AND signingKeyFingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(type: ExternalExtensionType, fingerprint: String): ExternalExtensionRepoEntity?

    @Upsert
    suspend fun upsert(entity: ExternalExtensionRepoEntity)

    @Query("DELETE FROM external_extension_repos WHERE type = :type AND baseUrl = :baseUrl")
    suspend fun delete(type: ExternalExtensionType, baseUrl: String)
}
