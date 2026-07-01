package io.github.landwarderer.futon.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration28To29 : Migration(28, 29) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `download_queue` (
				`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
				`manga_id` INTEGER NOT NULL,
				`chapters_ids` TEXT NOT NULL,
				`priority` INTEGER NOT NULL,
				`created_at` INTEGER NOT NULL,
				`wifi_only` INTEGER NOT NULL,
				`charging_only` INTEGER NOT NULL,
				`off_peak_only` INTEGER NOT NULL,
				FOREIGN KEY(`manga_id`) REFERENCES `manga`(`manga_id`) ON UPDATE NO ACTION ON DELETE CASCADE
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_queue_manga_id` ON `download_queue` (`manga_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_title` ON `tags` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_queue_priority` ON `download_queue` (`priority`)")
	}
}
