package io.github.landwarderer.futon.local.data.index

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.data.input.LocalMangaParser
import io.github.landwarderer.futon.local.domain.model.LocalManga
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LocalMangaIndex @Inject constructor(
	private val mangaDataRepository: MangaDataRepository,
	private val db: MangaDatabase,
	@ApplicationContext context: Context,
	private val localMangaRepositoryProvider: Provider<LocalMangaRepository>,
) : FlowCollector<LocalManga?> {

	private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
	private val mutex = Mutex()

	private var currentVersion: Int
		get() = prefs.getInt(KEY_VERSION, 0)
		set(value) = prefs.edit { putInt(KEY_VERSION, value) }

	override suspend fun emit(value: LocalManga?) {
		if (value != null) {
			put(value)
		}
	}

	suspend fun update() = mutex.withLock {
		rebuild()
	}

	suspend fun updateIfRequired() {
		if (isUpdateRequired()) {
			mutex.withLock {
				// The first build may have finished while this caller was waiting for it
				if (isUpdateRequired()) {
					rebuild()
				}
			}
		}
	}

	suspend fun get(mangaId: Long, withDetails: Boolean): LocalManga? {
		updateIfRequired()
		// A rebuild in progress leaves the previous index in place until it commits, so a miss here
		// is an answer rather than a reason to wait for the whole storage scan
		val path = db.getLocalMangaIndexDao().findPath(mangaId) ?: return null
		return runCatchingCancellable {
			LocalMangaParser(File(path)).getManga(withDetails)
		}.onFailure {
			it.printStackTraceDebug("LocalMangaIndex::get")
		}.getOrNull()
	}

	suspend operator fun contains(mangaId: Long): Boolean {
		return db.getLocalMangaIndexDao().findPath(mangaId) != null
	}

	suspend fun put(manga: LocalManga) = mutex.withLock {
		db.withTransaction {
			upsert(manga)
		}
	}

	suspend fun delete(mangaId: Long) {
		db.getLocalMangaIndexDao().delete(mangaId)
	}

	suspend fun getAvailableTags(skipNsfw: Boolean): List<String> {
		val dao = db.getLocalMangaIndexDao()
		return if (skipNsfw) {
			dao.findTags(isNsfw = false)
		} else {
			dao.findTags()
		}
	}

	/**
	 * Scans local storage first and writes the index afterwards, in one short transaction.
	 *
	 * The scan opens every archive and folder in every storage directory: minutes on a large library
	 * or slow storage, and forever if a read hangs. It used to run inside the transaction, holding
	 * SQLite's only write connection the whole time. Every other transaction queued behind it, and so
	 * did every newly subscribed Room Flow, since subscribing installs triggers through the writer.
	 * Those waits block threads of Room's 4-thread executor, and once it is exhausted nothing can be
	 * read at all - history, favourites and sources stay empty. This runs on every cold start.
	 */
	private suspend fun rebuild() {
		println("LocalMangaIndex: Starting update")
		runCatchingCancellable {
			val found = localMangaRepositoryProvider.get()
				.getRawListAsFlow()
				.toList()
			db.withTransaction {
				db.getLocalMangaIndexDao().clear()
				found.forEach {
					println("LocalMangaIndex: Found manga ${it.manga.title} at ${it.file.path}")
					upsert(it)
				}
			}
			currentVersion = VERSION
			println("LocalMangaIndex: Update completed")
		}.onFailure {
			it.printStackTraceDebug("LocalMangaIndex::update")
		}
	}

	private suspend fun upsert(manga: LocalManga) {
		mangaDataRepository.storeManga(manga.manga, replaceExisting = true)
		db.getLocalMangaIndexDao().upsert(manga.toEntity())
	}

	private fun LocalManga.toEntity() = LocalMangaIndexEntity(
		mangaId = manga.id,
		path = file.path,
	)

	private fun isUpdateRequired() = currentVersion < VERSION

	companion object {

		private const val PREF_NAME = "_local_index"
		private const val KEY_VERSION = "ver"
		private const val VERSION = 1
	}
}
