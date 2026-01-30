package io.github.landwarderer.futon.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.landwarderer.futon.core.db.TABLE_TACHIYOMI_EXTENSIONS

@Entity(tableName = TABLE_TACHIYOMI_EXTENSIONS)
data class TachiyomiExtensionEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "pkg_name")
	val pkgName: String,
	@ColumnInfo(name = "name") val name: String,
	@ColumnInfo(name = "version_name") val versionName: String,
	@ColumnInfo(name = "version_code") val versionCode: Long,
	@ColumnInfo(name = "lib_version") val libVersion: Double,
	@ColumnInfo(name = "is_nsfw") val isNsfw: Boolean,
	@ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
	@ColumnInfo(name = "is_shared") val isShared: Boolean,
	@ColumnInfo(name = "installed_at") val installedAt: Long,
)
