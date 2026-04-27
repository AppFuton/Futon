package eu.kanade.tachiyomi

import io.github.landwarderer.futon.BuildConfig

/**
 * Stub class for Mihon extensions that reference AppInfo.
 * Extensions may call these methods for User-Agent strings or version checks.
 *
 * @since extension-lib 1.3
 */
@Suppress("UNUSED")
object AppInfo {
    /**
     * Version code of the host application.
     */
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    /**
     * Version name of the host application.
     */
    fun getVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * Supported image MIME types by the reader.
     */
    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/avif",
        "image/heif",
        "image/jxl",
    )
}
