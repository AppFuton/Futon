package io.github.landwarderer.futon.download.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.landwarderer.futon.core.db.TABLE_DOWNLOAD_QUEUE
import io.github.landwarderer.futon.core.db.entity.MangaEntity

@Entity(
    tableName = TABLE_DOWNLOAD_QUEUE,
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["manga_id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["manga_id"]),
        Index(value = ["priority"]),
    ],
)
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "manga_id")
    val mangaId: Long,
    @ColumnInfo(name = "chapters_ids")
    val chaptersIds: LongArray,
    @ColumnInfo(name = "priority")
    val priority: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "wifi_only")
    val wifiOnly: Boolean = true,
    @ColumnInfo(name = "charging_only")
    val charging_only: Boolean = false,
    @ColumnInfo(name = "off_peak_only")
    val offPeakOnly: Boolean = false,
    @ColumnInfo(name = "is_paused")
    val isPaused: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DownloadQueueEntity

        if (id != other.id) return false
        if (mangaId != other.mangaId) return false
        if (!chaptersIds.contentEquals(other.chaptersIds)) return false
        if (priority != other.priority) return false
        if (createdAt != other.createdAt) return false
        if (wifiOnly != other.wifiOnly) return false
        if (charging_only != other.charging_only) return false
        if (offPeakOnly != other.offPeakOnly) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mangaId.hashCode()
        result = 31 * result + chaptersIds.contentHashCode()
        result = 31 * result + priority
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + wifiOnly.hashCode()
        result = 31 * result + charging_only.hashCode()
        result = 31 * result + offPeakOnly.hashCode()
        return result
    }
}
