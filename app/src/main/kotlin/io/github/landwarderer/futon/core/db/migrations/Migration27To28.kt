package io.github.landwarderer.futon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS external_extension_repos (
				id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
				type TEXT NOT NULL,
				baseUrl TEXT NOT NULL,
				name TEXT NOT NULL,
				shortName TEXT,
				website TEXT NOT NULL,
				signingKeyFingerprint TEXT NOT NULL,
				createdAt INTEGER NOT NULL,
				updatedAt INTEGER NOT NULL,
				lastSuccessAt INTEGER NOT NULL,
				lastError TEXT,
				version TEXT
			)
			""".trimIndent()
		)
		db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_extension_repos_type_baseUrl` ON `external_extension_repos` (`type`, `baseUrl`)")
		db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_extension_repos_type_signingKeyFingerprint` ON `external_extension_repos` (`type`, `signingKeyFingerprint`)")
	}
}
