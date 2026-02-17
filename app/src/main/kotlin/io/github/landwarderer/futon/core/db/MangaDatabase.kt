package io.github.landwarderer.futon.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.bookmarks.data.BookmarkEntity
import io.github.landwarderer.futon.bookmarks.data.BookmarksDao
import io.github.landwarderer.futon.core.db.dao.ChaptersDao
import io.github.landwarderer.futon.core.db.dao.MangaDao
import io.github.landwarderer.futon.core.db.dao.MangaSourcesDao
import io.github.landwarderer.futon.core.db.dao.PreferencesDao
import io.github.landwarderer.futon.core.db.dao.TagsDao
import io.github.landwarderer.futon.core.db.dao.TrackLogsDao
import io.github.landwarderer.futon.core.db.entity.ChapterEntity
import io.github.landwarderer.futon.core.db.entity.MangaEntity
import io.github.landwarderer.futon.core.db.entity.MangaPrefsEntity
import io.github.landwarderer.futon.core.db.entity.MangaSourceEntity
import io.github.landwarderer.futon.core.db.entity.MangaTagsEntity
import io.github.landwarderer.futon.core.db.entity.TagEntity
import io.github.landwarderer.futon.core.db.migrations.Migration10To11
import io.github.landwarderer.futon.core.db.migrations.Migration11To12
import io.github.landwarderer.futon.core.db.migrations.Migration12To13
import io.github.landwarderer.futon.core.db.migrations.Migration13To14
import io.github.landwarderer.futon.core.db.migrations.Migration14To15
import io.github.landwarderer.futon.core.db.migrations.Migration15To16
import io.github.landwarderer.futon.core.db.migrations.Migration16To17
import io.github.landwarderer.futon.core.db.migrations.Migration17To18
import io.github.landwarderer.futon.core.db.migrations.Migration18To19
import io.github.landwarderer.futon.core.db.migrations.Migration19To20
import io.github.landwarderer.futon.core.db.migrations.Migration1To2
import io.github.landwarderer.futon.core.db.migrations.Migration20To21
import io.github.landwarderer.futon.core.db.migrations.Migration21To22
import io.github.landwarderer.futon.core.db.migrations.Migration22To23
import io.github.landwarderer.futon.core.db.migrations.Migration23To24
import io.github.landwarderer.futon.core.db.migrations.Migration24To23
import io.github.landwarderer.futon.core.db.migrations.Migration24To25
import io.github.landwarderer.futon.core.db.migrations.Migration25To26
import io.github.landwarderer.futon.core.db.migrations.Migration26To27
import io.github.landwarderer.futon.core.db.migrations.Migration2To3
import io.github.landwarderer.futon.core.db.migrations.Migration3To4
import io.github.landwarderer.futon.core.db.migrations.Migration4To5
import io.github.landwarderer.futon.core.db.migrations.Migration5To6
import io.github.landwarderer.futon.core.db.migrations.Migration6To7
import io.github.landwarderer.futon.core.db.migrations.Migration7To8
import io.github.landwarderer.futon.core.db.migrations.Migration8To9
import io.github.landwarderer.futon.core.db.migrations.Migration9To10
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.favourites.data.FavouriteCategoriesDao
import io.github.landwarderer.futon.favourites.data.FavouriteCategoryEntity
import io.github.landwarderer.futon.favourites.data.FavouriteEntity
import io.github.landwarderer.futon.favourites.data.FavouritesDao
import io.github.landwarderer.futon.history.data.HistoryDao
import io.github.landwarderer.futon.history.data.HistoryEntity
import io.github.landwarderer.futon.local.data.index.LocalMangaIndexDao
import io.github.landwarderer.futon.local.data.index.LocalMangaIndexEntity
import io.github.landwarderer.futon.scrobbling.common.data.ScrobblingDao
import io.github.landwarderer.futon.scrobbling.common.data.ScrobblingEntity
import io.github.landwarderer.futon.stats.data.StatsDao
import io.github.landwarderer.futon.stats.data.StatsEntity
import io.github.landwarderer.futon.suggestions.data.SuggestionDao
import io.github.landwarderer.futon.suggestions.data.SuggestionEntity
import io.github.landwarderer.futon.tracker.data.TrackEntity
import io.github.landwarderer.futon.tracker.data.TrackLogEntity
import io.github.landwarderer.futon.tracker.data.TracksDao

const val DATABASE_VERSION = 27

@Database(
	entities = [
		MangaEntity::class, TagEntity::class, HistoryEntity::class, MangaTagsEntity::class, ChapterEntity::class,
		FavouriteCategoryEntity::class, FavouriteEntity::class, MangaPrefsEntity::class, TrackEntity::class,
		TrackLogEntity::class, SuggestionEntity::class, BookmarkEntity::class, ScrobblingEntity::class,
		MangaSourceEntity::class, StatsEntity::class, LocalMangaIndexEntity::class,
	],
	version = DATABASE_VERSION,
)
abstract class MangaDatabase : RoomDatabase() {

	abstract fun getHistoryDao(): HistoryDao

	abstract fun getTagsDao(): TagsDao

	abstract fun getMangaDao(): MangaDao

	abstract fun getFavouritesDao(): FavouritesDao

	abstract fun getPreferencesDao(): PreferencesDao

	abstract fun getFavouriteCategoriesDao(): FavouriteCategoriesDao

	abstract fun getTracksDao(): TracksDao

	abstract fun getTrackLogsDao(): TrackLogsDao

	abstract fun getSuggestionDao(): SuggestionDao

	abstract fun getBookmarksDao(): BookmarksDao

	abstract fun getScrobblingDao(): ScrobblingDao

	abstract fun getSourcesDao(): MangaSourcesDao

	abstract fun getStatsDao(): StatsDao

	abstract fun getLocalMangaIndexDao(): LocalMangaIndexDao

	abstract fun getChaptersDao(): ChaptersDao
}

fun getDatabaseMigrations(context: Context): Array<Migration> = arrayOf(
	Migration1To2(),
	Migration2To3(),
	Migration3To4(),
	Migration4To5(),
	Migration5To6(),
	Migration6To7(),
	Migration7To8(),
	Migration8To9(),
	Migration9To10(),
	Migration10To11(),
	Migration11To12(),
	Migration12To13(),
	Migration13To14(),
	Migration14To15(),
	Migration15To16(),
	Migration16To17(context),
	Migration17To18(),
	Migration18To19(),
	Migration19To20(),
	Migration20To21(),
	Migration21To22(),
	Migration22To23(),
	Migration23To24(),
	Migration24To23(),
	Migration24To25(),
	Migration25To26(),
	Migration26To27(),
)

fun MangaDatabase(context: Context): MangaDatabase = Room
	.databaseBuilder(context, MangaDatabase::class.java, "futon-db")
	.addMigrations(*getDatabaseMigrations(context))
	.addCallback(DatabasePrePopulateCallback(context.resources))
	.build()

fun InvalidationTracker.removeObserverAsync(observer: InvalidationTracker.Observer) {
	val scope = processLifecycleScope
	if (scope.isActive) {
		processLifecycleScope.launch(Dispatchers.IO, CoroutineStart.ATOMIC) {
			removeObserver(observer)
		}
	}
}
