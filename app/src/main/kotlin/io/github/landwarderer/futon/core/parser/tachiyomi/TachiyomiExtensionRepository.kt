package io.github.landwarderer.futon.core.parser.tachiyomi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.dao.TachiyomiExtensionsDao
import io.github.landwarderer.futon.core.db.entity.TachiyomiExtensionEntity
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TachiyomiExtensionRepository @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: MangaDatabase,
	private val loader: TachiyomiExtensionLoader,
	private val settings: AppSettings,
) {

	private val dao: TachiyomiExtensionsDao
		get() = database.getTachiyomiExtensionsDao()

	private val isExtensionsLoaded = AtomicBoolean(false)

	suspend fun getEnabledExtensions(): List<TachiyomiExtension> {
		ensureExtensionsLoaded()
		return loadExtensionDetails(dao.findAllEnabled())
	}

	suspend fun getAllExtensions(): List<TachiyomiExtension> {
		ensureExtensionsLoaded()
		return loadExtensionDetails(dao.findAll())
	}

	suspend fun getExtension(pkgName: String): TachiyomiExtension? {
		ensureExtensionsLoaded()
		val entity = dao.find(pkgName) ?: return null
		return loadExtensionDetails(listOf(entity)).firstOrNull()
	}

	fun observeEnabledExtensions(): Flow<List<TachiyomiExtension>> {
		return observePackageChanges()
			.onStart { emit(Unit) }
			.onEach { 
				isExtensionsLoaded.set(false)
				ensureExtensionsLoaded()
			}
			.flatMapLatest {
				dao.observeEnabled()
					.map { entities -> loadExtensionDetails(entities) }
			}
	}

	fun observeAllExtensions(): Flow<List<TachiyomiExtension>> {
		return observePackageChanges()
			.onStart { emit(Unit) }
			.onEach { 
				isExtensionsLoaded.set(false)
				ensureExtensionsLoaded()
			}
			.flatMapLatest {
				dao.observeAll()
					.map { entities -> loadExtensionDetails(entities) }
			}
	}

	fun observeExtensionCount(): Flow<Int> {
		return observePackageChanges()
			.onStart { emit(Unit) }
			.onEach { 
				isExtensionsLoaded.set(false)
				ensureExtensionsLoaded()
			}
			.flatMapLatest {
				dao.observeEnabled()
					.map { it.size }
					.distinctUntilChanged()
			}
	}

	suspend fun setExtensionEnabled(pkgName: String, isEnabled: Boolean) {
		dao.setEnabled(pkgName, isEnabled)
	}

	suspend fun setAllExtensionsEnabled(isEnabled: Boolean) {
		dao.setAllEnabled(isEnabled)
	}

	suspend fun refreshExtensions() {
		isExtensionsLoaded.set(false)
		ensureExtensionsLoaded()
	}

	fun observePackageChanges(): Flow<Unit> {
		return callbackFlow {
			val receiver = object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					trySendBlocking(Unit)
				}
			}

			ContextCompat.registerReceiver(
				context,
				receiver,
				IntentFilter().apply {
					addAction(Intent.ACTION_PACKAGE_ADDED)
					addAction(Intent.ACTION_PACKAGE_REPLACED)
					addAction(Intent.ACTION_PACKAGE_REMOVED)
					addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
					addDataScheme("package")
				},
				ContextCompat.RECEIVER_EXPORTED,
			)

			awaitClose { context.unregisterReceiver(receiver) }
		}
	}

	private suspend fun ensureExtensionsLoaded() {
		if (isExtensionsLoaded.getAndSet(true)) {
			return
		}

		withContext(Dispatchers.Default) {
			try {
				val loadResults = loader.loadExtensions()
				val entities = loadResults.mapNotNull { result ->
					when (result) {
						is ExtensionLoadResult.Success -> result.extension.toEntity()
						else -> null
					}
				}

				database.withTransaction {
					dao.sync(entities)
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
				isExtensionsLoaded.set(false)
			}
		}
	}

	private suspend fun loadExtensionDetails(entities: List<TachiyomiExtensionEntity>): List<TachiyomiExtension> {
		return withContext(Dispatchers.Default) {
			val loadResults = loader.loadExtensions()
			val extensionsByPkg = loadResults.mapNotNull { result ->
				when (result) {
					is ExtensionLoadResult.Success -> result.extension.pkgName to result.extension
					else -> null
				}
			}.toMap()

			entities.mapNotNull { entity ->
				extensionsByPkg[entity.pkgName]?.copy(
					isEnabled = entity.isEnabled,
				)
			}
		}
	}

	private fun TachiyomiExtension.toEntity(): TachiyomiExtensionEntity {
		return TachiyomiExtensionEntity(
			pkgName = pkgName,
			name = name,
			versionName = versionName,
			versionCode = versionCode,
			libVersion = libVersion,
			isNsfw = isNsfw,
			isEnabled = isEnabled,
			isShared = isShared,
			installedAt = System.currentTimeMillis(),
		)
	}
}
