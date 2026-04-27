package io.github.landwarderer.futon.mihon

import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for getting Mihon sources to display in the UI.
 */
@Singleton
class GetMihonSourcesUseCase @Inject constructor(
    private val extensionManager: MihonExtensionManager,
    private val settings: io.github.landwarderer.futon.core.prefs.AppSettings,
) {
    
    fun getSourcesFlow(): Flow<List<MihonSourceItem>> {
        return extensionManager.installedExtensions.map { extensions ->
            val allSources = extensions.flatMap { ext ->
                ext.catalogueSources.map { catalogueSource ->
                    Triple(ext, catalogueSource, catalogueSource.name)
                }
            }
            
            val nameCountMap = allSources.groupBy { it.third }.mapValues { it.value.size }
            
            allSources.map { (ext, catalogueSource, baseName) ->
                val needsLanguageSuffix = nameCountMap[baseName]?.let { it > 1 } ?: false
                
                MihonSourceItem(
                    source = MihonMangaSource(
                        catalogueSource = catalogueSource,
                        pkgName = ext.pkgName,
                        isNsfw = ext.isNsfw,
                    ),
                    extensionName = ext.appName,
                    versionName = ext.versionName,
                    hasLanguageSuffix = needsLanguageSuffix,
                )
            }
        }
    }
    
    fun getSourcesFlowFiltered(userLanguages: Set<String>): Flow<List<MihonSourceItem>> {
        return getSourcesFlow()
    }
    
    fun getSourcesByLanguage(): Map<String, List<MihonMangaSource>> {
        return extensionManager.getSourcesByLanguage().mapValues { (_, sources) ->
            sources.map { catalogueSource ->
                val ext = extensionManager.installedExtensions.value.find { 
                    it.sources.contains(catalogueSource) 
                }
                MihonMangaSource(
                    catalogueSource = catalogueSource,
                    pkgName = ext?.pkgName ?: "",
                    isNsfw = ext?.isNsfw ?: false,
                )
            }
        }
    }
    
    fun hasExtensions(): Boolean = extensionManager.hasExtensions()
    
    fun isLoading(): Flow<Boolean> = extensionManager.isLoading
}

data class MihonSourceItem(
    val source: MihonMangaSource,
    val extensionName: String,
    val versionName: String,
    val hasLanguageSuffix: Boolean = false,
) {
    val displayName: String get() {
        return if (hasLanguageSuffix) {
            "${source.displayName} (${getLanguageDisplayName(language)})"
        } else {
            source.displayName
        }
    }
    
    val language: String get() = source.language
    val isNsfw: Boolean get() = source.isNsfw
    val sourceId: Long get() = source.sourceId
    
    companion object {
        private fun getLanguageDisplayName(langCode: String): String {
            return when (langCode.lowercase()) {
                "zh" -> "ä¸­æ–‡"
                "zh-hans" -> "ç®€ä½“ä¸­æ–‡"
                "zh-hant" -> "ç¹é«”ä¸­æ–‡"
                "en" -> "English"
                "ja" -> "æ—¥æœ¬èªž"
                "ko" -> "í•œêµ­ì–´"
                "all" -> "Multi"
                else -> langCode.uppercase()
            }
        }
    }
}
