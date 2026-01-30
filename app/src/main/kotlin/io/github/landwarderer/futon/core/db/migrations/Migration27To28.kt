package io.github.landwarderer.futon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS tachiyomi_extensions (
				pkg_name TEXT NOT NULL PRIMARY KEY,
				name TEXT NOT NULL,
				version_name TEXT NOT NULL,
				version_code INTEGER NOT NULL,
				lib_version REAL NOT NULL,
				is_nsfw INTEGER NOT NULL,
				is_enabled INTEGER NOT NULL,
				is_shared INTEGER NOT NULL,
				installed_at INTEGER NOT NULL
			)
			""".trimIndent(),
		)
	}
}
