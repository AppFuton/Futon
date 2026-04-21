package io.github.landwarderer.futon.mihon

import android.content.Context
import android.content.pm.PackageInfo
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.github.landwarderer.futon.mihon.compat.MihonInjektBridge
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionLoaderSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionMetadataSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionSourceLoaderSupport
import io.github.landwarderer.futon.mihon.model.MihonExtensionInfo
import io.github.landwarderer.futon.mihon.model.MihonLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loader for Mihon extension APKs.
 * 
 * Scans for installed Mihon extensions and loads their Source implementations.
 */
@Singleton
class MihonExtensionLoader @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val injektBridge: dagger.Lazy<MihonInjektBridge>,
) {
    companion object {
        private const val TAG = "MihonExtensionLoader"
        
        // Feature that marks an APK as a Mihon/Tachiyomi extension
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        
        // Metadata keys in AndroidManifest.xml
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        
        // Supported library version range
        const val LIB_VERSION_MIN = 1.2
        const val LIB_VERSION_MAX = 1.9
        
    }
    
    /**
     * Load all installed Mihon extensions.
     * 
     * @param context Android context
     * @return List of load results (success, error, or untrusted)
     */
    suspend fun loadExtensions(context: Context): List<MihonLoadResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Mihon extension loading...")
            // Ensure Injekt is initialized before loading any extensions
            injektBridge.get().initialize()
            
            val pkgManager = context.packageManager
            
            // Get all installed packages
            val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
            Log.d(TAG, "Scanning ${installedPkgs.size} packages...")
            
            // Filter to only extension packages
            val extPkgs = installedPkgs.filter { pkg: PackageInfo ->
                val pkgName = pkg.packageName
                
                // First filter by name to avoid refreshing all apps
                if (!ExternalExtensionLoaderSupport.looksLikeMihonPackage(pkgName)) {
                    return@filter false
                }
                
                Log.d(TAG, "Potential extension found: $pkgName. Refreshing info...")
                
                // Refresh to ensure we have metadata and features
                val completePkg = ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(pkgManager, pkg)
                val isExt = isPackageAnExtension(completePkg)
                
                Log.d(TAG, "Package $pkgName: isExt=$isExt")
                isExt
            }
            
            if (extPkgs.isEmpty()) {
                Log.d(TAG, "No Mihon extensions found")
                return@withContext emptyList()
            }
            
            Log.i(TAG, "Found ${extPkgs.size} Mihon extension(s) to load")
            
            // Load extensions in parallel
            extPkgs.map { pkgInfo: PackageInfo ->
                async { 
                    try {
                        // Re-fetch full info for loading to be safe
                        val completePkg = ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(pkgManager, pkgInfo)
                        loadExtension(context, completePkg)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to load extension ${pkgInfo.packageName}", e)
                        MihonLoadResult.Error(pkgInfo.packageName, "Exception: ${e.message}", e)
                    }
                }
            }.awaitAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load extensions", e)
            emptyList()
        }
    }
    
    /**
     * Load a single Mihon extension by package name.
     */
    suspend fun loadExtension(context: Context, packageName: String): MihonLoadResult? = withContext(Dispatchers.IO) {
        injektBridge.get().initialize()
        
        val pkgManager = context.packageManager
        val pkgInfo = ExternalExtensionLoaderSupport.getPackageInfoOrNull(pkgManager, packageName)
            ?: return@withContext null
        
        if (!isPackageAnExtension(pkgInfo)) {
            return@withContext null
        }
        
        loadExtension(context, pkgInfo)
    }
    
    /**
     * Get list of installed Mihon extensions (metadata only, without loading).
     */
    fun getInstalledExtensions(context: Context): List<MihonExtensionInfo> {
        val pkgManager = context.packageManager
        val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
        
        return installedPkgs
            .filter { ExternalExtensionLoaderSupport.looksLikeMihonPackage(it.packageName) }
            .map { ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(pkgManager, it) }
            .filter { isPackageAnExtension(it) }
            .mapNotNull { extractExtensionInfo(it) }
    }
    
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        
        // Method 1: Check for explicit feature declaration
        val hasFeature = pkgInfo.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true
        
        // Method 2: Check for package naming convention
        val hasPackageName = ExternalExtensionLoaderSupport.looksLikeMihonPackage(pkgName)
        
        // Method 3: Check for metadata in application info
        val hasMetaData = ExternalExtensionMetadataSupport.hasDeclaredSource(
            metaData = pkgInfo.applicationInfo?.metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
        )
        
        // A package is an extension if it has the feature OR (has the correct name prefix AND has metadata)
        val isExtension = hasFeature || (hasPackageName && hasMetaData)
        
        if (hasPackageName && !isExtension) {
            Log.w(TAG, "Package $pkgName looks like an extension but lacks feature and metadata")
        }
        
        return isExtension
    }
    
    private fun extractExtensionInfo(pkgInfo: PackageInfo): MihonExtensionInfo? {
        val completePkgInfo = pkgInfo
        val pkgName = completePkgInfo.packageName
        val appInfo = completePkgInfo.applicationInfo ?: run {
            Log.w(TAG, "extractExtensionInfo($pkgName): skipped because applicationInfo is null")
            return null
        }
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo) ?: run {
            Log.w(TAG, "extractExtensionInfo($pkgName): skipped because metaData is null")
            return null
        }
        
        val versionName = completePkgInfo.versionName ?: run {
            Log.w(TAG, "extractExtensionInfo($pkgName): skipped because versionName is null")
            return null
        }
        
        // Extract library version - handles different version formats
        val libVersion = try {
            versionName.split('.').let { parts ->
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble()
                else parts[0].toDouble()
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractExtensionInfo($pkgName): Failed to parse libVersion from $versionName, defaulting to 1.4")
            1.4 // Default to 1.4 if parsing fails
        }
        
        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        ) ?: run {
            Log.w(TAG, "extractExtensionInfo($pkgName): skipped because no declaredSource could be parsed. Keys present in manifest: ${metaData.keySet()?.joinToString()}")
            return null
        }
        
        // Get app name safely
        val appName = try {
            ExternalExtensionLoaderSupport.getAppLabel(applicationContext, appInfo)
        } catch (e: Exception) {
            null
        } ?: pkgInfo.packageName.substringAfterLast('.')
        
        val lang = ExternalExtensionLoaderSupport.extractLanguage(completePkgInfo.packageName, "extension")
        
        return MihonExtensionInfo(
            pkgName = completePkgInfo.packageName,
            appName = appName,
            versionCode = PackageInfoCompat.getLongVersionCode(completePkgInfo),
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = declaredSource.isNsfw,
            sourceClassName = declaredSource.sourceClassName,
            apkPath = appInfo.sourceDir ?: return null,
        )
    }
    
    private fun loadExtension(context: Context, pkgInfo: PackageInfo): MihonLoadResult {
        val pkgName = pkgInfo.packageName
        val appInfo = pkgInfo.applicationInfo
            ?: run {
                Log.e(TAG, "loadExtension($pkgName) FAILED: No ApplicationInfo")
                return MihonLoadResult.Error(pkgName, "No ApplicationInfo")
            }
        
        val versionName = pkgInfo.versionName
            ?: run {
                Log.e(TAG, "loadExtension($pkgName) FAILED: No version name")
                return MihonLoadResult.Error(pkgName, "No version name")
            }
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        
        // Extract library version
        val libVersion = try {
            versionName.split('.').let { parts ->
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble()
                else parts[0].toDouble()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadExtension($pkgName) FAILED: Invalid lib version format ($versionName)")
            return MihonLoadResult.Error(pkgName, "Invalid lib version format: $versionName")
        }
        
        // Check library version compatibility (more relaxed check)
        if (libVersion < LIB_VERSION_MIN) {
            val err = "Extension lib version too old: $libVersion (min: $LIB_VERSION_MIN)"
            Log.e(TAG, "loadExtension($pkgName) FAILED: $err")
            return MihonLoadResult.Error(pkgName, err)
        }
        
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo)
            ?: run {
                Log.e(TAG, "loadExtension($pkgName) FAILED: No meta-data in manifest")
                return MihonLoadResult.Error(pkgName, "No meta-data in manifest")
            }
        
        // Get source class name(s)
        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        ) ?: run {
            Log.e(TAG, "loadExtension($pkgName) FAILED: No valid source class specified in manifest")
            return MihonLoadResult.Error(pkgName, "No source class specified in manifest")
        }
        
        // Get app name and language
        val appName = ExternalExtensionLoaderSupport.getAppLabel(context, appInfo)
        val lang = ExternalExtensionLoaderSupport.extractLanguage(pkgName, "extension")
        
        Log.d(TAG, "Loading extension: $pkgName (lib $libVersion, $lang)")
        
        // Create ClassLoader for this extension
        val classLoader = try {
            Log.d(TAG, "Creating ClassLoader for $pkgName with sourceDir: ${appInfo.sourceDir}")
            ChildFirstPathClassLoader(
                appInfo.sourceDir,
                appInfo.nativeLibraryDir,
                context.classLoader
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create ClassLoader for $pkgName", e)
            return MihonLoadResult.Error(pkgName, "Failed to create ClassLoader", e)
        }
        
        // Load source classes
        val sources = try {
            loadSources(pkgName, declaredSource.sourceClassName, classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load sources from $pkgName", e)
            return MihonLoadResult.Error(pkgName, "Failed to load sources: ${e.message}", e)
        }
        
        if (sources.isEmpty()) {
            Log.e(TAG, "No sources loaded from $pkgName")
            return MihonLoadResult.Error(pkgName, "No sources loaded from extension")
        } else {
            Log.i(TAG, "Successfully loaded ${sources.size} source(s) from $pkgName")
        }
        
        return MihonLoadResult.Success(
            pkgName = pkgName,
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = declaredSource.isNsfw,
            sources = sources,
        )
    }
    
    private fun loadSources(
        pkgName: String,
        sourceClassNames: String,
        classLoader: ClassLoader,
    ): List<Source> {
        return ExternalExtensionSourceLoaderSupport.loadSources(
            pkgName = pkgName,
            sourceClassNames = sourceClassNames,
            classLoader = classLoader,
            asSource = { it as? Source },
            createSourcesFromFactory = { (it as? SourceFactory)?.createSources() },
            onUnknownInstance = { className ->
                Log.w(TAG, "Unknown instance type in $pkgName: $className")
            },
        )
    }
    
}
