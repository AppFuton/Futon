package io.github.landwarderer.futon.core.db

import android.content.res.Resources
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.landwarderer.futon.R
import org.koitharu.kotatsu.parsers.model.SortOrder

class DatabasePrePopulateCallback(private val resources: Resources) : RoomDatabase.Callback() {

	override fun onCreate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"INSERT INTO favourite_categories (created_at, sort_key, title, `order`, track, show_in_lib, `deleted_at`) VALUES (?,?,?,?,?,?,?)",
			arrayOf(
				System.currentTimeMillis(),
				1,
				resources.getString(R.string.read_later),
				SortOrder.NEWEST.name,
				1,
				1,
				0L,
			)
		)

		val now = System.currentTimeMillis()
		db.execSQL(
			"INSERT INTO external_extension_repos (type, baseUrl, name, shortName, website, signingKeyFingerprint, createdAt, updatedAt, lastSuccessAt) VALUES (?,?,?,?,?,?,?,?,?)",
			arrayOf(
				"MIHON",
				"https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo",
				"Keiyoushi",
				"Keiyoushi",
				"https://keiyoushi.github.io/extensions",
				"508c909405615d0234a41316b230230559f6b9a89c3f15c13b306b38c2306f50",
				now,
				now,
				now,
			)
		)
	}
}
