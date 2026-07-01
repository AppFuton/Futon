package io.github.landwarderer.futon.download.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.landwarderer.futon.download.data.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {

    @Query("SELECT * FROM download_queue ORDER BY priority ASC")
    fun observeAll(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue ORDER BY priority ASC")
    suspend fun getAll(): List<DownloadQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadQueueEntity): Long

    @Update
    suspend fun update(entity: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_queue")
    suspend fun deleteAll()

    @Query("SELECT MAX(priority) FROM download_queue")
    suspend fun getMaxPriority(): Int?

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            updatePriority(id, index)
        }
    }

    @Query("UPDATE download_queue SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)
}
