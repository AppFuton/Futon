package io.github.landwarderer.futon.core.parser.tachiyomi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.parsers.model.MangaSource
import javax.inject.Inject

data class TachiyomiMangaSource(
	val sourceId: Long,
) : MangaSource {

	override val name: String
		get() = "tachiyomi:$sourceId"

	private var cachedName: String? = null
	private var cachedExtensionPkgName: String? = null

	fun resolveName(context: Context): String {
		cachedName?.let { return it }

		val repository = TachiyomiExtensionRepositoryHolder.get(context)
		return repository?.let { repo ->
			val extensions = runCatching { kotlinx.coroutines.runBlocking { repo.getAllExtensions() } }.getOrNull()
			val source = extensions?.flatMap { it.sources }?.find { it.id == sourceId }
			source?.displayName?.also {
				cachedName = it
				cachedExtensionPkgName = extensions.find { ext -> ext.sources.contains(source) }?.pkgName
			}
		} ?: "Tachiyomi Source #$sourceId"
	}

	fun getExtensionPackageName(): String? = cachedExtensionPkgName
}

internal object TachiyomiExtensionRepositoryHolder {
	private var repository: TachiyomiExtensionRepository? = null

	fun set(repo: TachiyomiExtensionRepository) {
		repository = repo
	}

	fun get(context: Context): TachiyomiExtensionRepository? {
		if (repository == null) {
			repository = runCatching {
				val appContext = context.applicationContext
				val componentClass = Class.forName("dagger.hilt.android.internal.managers.ApplicationComponentManager")
				val component = componentClass.getMethod("generatedComponent").invoke(appContext)
				val repoField = component.javaClass.methods.find {
					it.name.contains("getTachiyomiExtensionRepository", ignoreCase = true)
				}
				repoField?.invoke(component) as? TachiyomiExtensionRepository
			}.getOrNull()
		}
		return repository
	}
}
