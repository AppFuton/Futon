package io.github.landwarderer.futon.mihon.extensions.repo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.db.MangaDatabase
import io.github.landwarderer.futon.core.db.dao.ExternalExtensionRepoDao
import io.github.landwarderer.futon.core.db.entity.ExternalExtensionRepoEntity
import io.github.landwarderer.futon.core.util.ext.getDisplayMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalExtensionRepoRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: MangaDatabase,
    private val service: ExtensionRepoService,
) {

	private val dao: ExternalExtensionRepoDao
		get() = db.getExternalExtensionRepoDao()


	fun observeByType(type: ExternalExtensionType): Flow<List<ExternalExtensionRepo>> {
		return dao.observeByType(type).map { list -> list.map { it.toDomain() } }
	}

	suspend fun getByType(type: ExternalExtensionType): List<ExternalExtensionRepo> {
		return dao.getByType(type).map { it.toDomain() }
	}

	suspend fun addRepo(type: ExternalExtensionType, indexUrl: String): AddRepoResult {
		return when (val prepared = prepareAddRepo(type, indexUrl)) {
			is PrepareAddRepoResult.Ready -> confirmAddRepo(prepared.repo)
			is PrepareAddRepoResult.DuplicateFingerprint -> AddRepoResult.DuplicateFingerprint(prepared.existingRepo)
			is PrepareAddRepoResult.FetchFailed -> AddRepoResult.FetchFailed(prepared.error)
			PrepareAddRepoResult.InvalidUrl -> AddRepoResult.InvalidUrl
			PrepareAddRepoResult.RepoAlreadyExists -> AddRepoResult.RepoAlreadyExists
		}
	}

	suspend fun prepareAddRepo(type: ExternalExtensionType, indexUrl: String): PrepareAddRepoResult {
		Log.d(TAG, "prepareAddRepo:start type=$type input=$indexUrl")
		val normalizedIndexUrl = service.normalizeIndexUrl(indexUrl) ?: return PrepareAddRepoResult.InvalidUrl
			.also { Log.d(TAG, "prepareAddRepo:invalidUrl type=$type input=$indexUrl") }
		val baseUrl = service.baseUrlFromIndexUrl(normalizedIndexUrl)
		Log.d(TAG, "prepareAddRepo:normalized type=$type normalizedIndexUrl=$normalizedIndexUrl baseUrl=$baseUrl")
		if (dao.get(type, baseUrl) != null) {
			Log.d(TAG, "prepareAddRepo:duplicateBaseUrl type=$type baseUrl=$baseUrl")
			return PrepareAddRepoResult.RepoAlreadyExists
		}
		val repo = runCatching { service.fetchRepoDetails(baseUrl, type) }
			.onFailure { error ->
				Log.e(TAG, "prepareAddRepo:fetchFailed type=$type baseUrl=$baseUrl message=${error.message}", error)
			}
			.getOrElse { error ->
				return PrepareAddRepoResult.FetchFailed(error)
			}
		val duplicate = dao.getByFingerprint(type, repo.signingKeyFingerprint)
		if (duplicate != null) {
			Log.d(
				TAG,
				"prepareAddRepo:duplicateFingerprint type=$type baseUrl=$baseUrl fingerprint=${repo.signingKeyFingerprint} existingBaseUrl=${duplicate.baseUrl}",
			)
			return PrepareAddRepoResult.DuplicateFingerprint(duplicate.toDomain())
		}
		Log.d(TAG, "prepareAddRepo:ready type=$type baseUrl=$baseUrl name=${repo.displayName}")
		return PrepareAddRepoResult.Ready(repo)
	}

	suspend fun confirmAddRepo(repo: ExternalExtensionRepo): AddRepoResult {
		Log.d(TAG, "confirmAddRepo:start type=${repo.type} baseUrl=${repo.baseUrl} name=${repo.displayName}")
		if (dao.get(repo.type, repo.baseUrl) != null) {
			Log.d(TAG, "confirmAddRepo:duplicateBaseUrl type=${repo.type} baseUrl=${repo.baseUrl}")
			return AddRepoResult.RepoAlreadyExists
		}
		val duplicate = dao.getByFingerprint(repo.type, repo.signingKeyFingerprint)
		if (duplicate != null) {
			Log.d(
				TAG,
				"confirmAddRepo:duplicateFingerprint type=${repo.type} baseUrl=${repo.baseUrl} fingerprint=${repo.signingKeyFingerprint} existingBaseUrl=${duplicate.baseUrl}",
			)
			return AddRepoResult.DuplicateFingerprint(duplicate.toDomain())
		}
		dao.upsert(repo.toEntity())
		Log.d(TAG, "confirmAddRepo:success type=${repo.type} baseUrl=${repo.baseUrl} name=${repo.displayName}")
		return AddRepoResult.Success(repo)
	}

	suspend fun delete(repo: ExternalExtensionRepo) {
		dao.delete(repo.type, repo.baseUrl)
	}

	suspend fun refresh(type: ExternalExtensionType) {
		getByType(type).forEach { refresh(it) }
	}

	suspend fun refresh(repo: ExternalExtensionRepo) {
		val refreshed = runCatching { service.fetchRepoDetails(repo.baseUrl, repo.type) }
		val now = System.currentTimeMillis()
		val entity = if (refreshed.isSuccess) {
			refreshed.getOrThrow().copy(
				createdAt = repo.createdAt,
				updatedAt = now,
				lastSuccessAt = now,
				lastError = null,
			).toEntity()
		} else {
			val error = refreshed.exceptionOrNull()
			Log.e(TAG, "refresh:failed type=${repo.type} baseUrl=${repo.baseUrl} message=${error?.message}", error)
			repo.copy(
				updatedAt = now,
				lastError = error?.getDisplayMessage(appContext.resources)
					?: "Unknown error",
			).toEntity()
		}
		dao.upsert(entity)
	}

	suspend fun getAvailableExtensions(type: ExternalExtensionType): List<RepoAvailableExtension> = coroutineScope {
		getCatalogExtensions(type)
			.filter { it.isCompatible }
	}

	suspend fun getCatalogExtensions(type: ExternalExtensionType): List<RepoAvailableExtension> = coroutineScope {
		getByType(type)
			.map { repo -> async { service.fetchAvailableExtensions(repo) } }
			.awaitAll()
			.flatten()
			.groupBy { it.pkgName }
			.map { (_, list) -> list.maxByOrNull { it.versionCode }!! }
			.sortedWith(compareBy<RepoAvailableExtension> { it.lang }.thenBy { it.name.lowercase() })
	}

	sealed interface AddRepoResult {
		data class Success(val repo: ExternalExtensionRepo) : AddRepoResult
		data class DuplicateFingerprint(val existingRepo: ExternalExtensionRepo) : AddRepoResult
		data class FetchFailed(val error: Throwable) : AddRepoResult
		data object InvalidUrl : AddRepoResult
		data object RepoAlreadyExists : AddRepoResult
	}

	sealed interface PrepareAddRepoResult {
		data class Ready(val repo: ExternalExtensionRepo) : PrepareAddRepoResult
		data class DuplicateFingerprint(val existingRepo: ExternalExtensionRepo) : PrepareAddRepoResult
		data class FetchFailed(val error: Throwable) : PrepareAddRepoResult
		data object InvalidUrl : PrepareAddRepoResult
		data object RepoAlreadyExists : PrepareAddRepoResult
	}

	private companion object {
		const val TAG = "ExtensionRepo"
	}
}

private fun ExternalExtensionRepoEntity.toDomain(): ExternalExtensionRepo {
	return ExternalExtensionRepo(
		type = type,
		baseUrl = baseUrl,
		name = name,
		shortName = shortName,
		website = website,
		signingKeyFingerprint = signingKeyFingerprint,
		createdAt = createdAt,
		updatedAt = updatedAt,
		lastSuccessAt = lastSuccessAt,
		lastError = lastError,
		version = version,
	)
}

private fun ExternalExtensionRepo.toEntity(): ExternalExtensionRepoEntity {
	return ExternalExtensionRepoEntity(
		type = type,
		baseUrl = baseUrl,
		name = name,
		shortName = shortName,
		website = website,
		signingKeyFingerprint = signingKeyFingerprint,
		createdAt = createdAt,
		updatedAt = updatedAt,
		lastSuccessAt = lastSuccessAt,
		lastError = lastError,
		version = version,
	)
}
