package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.futon.mihon.parsers.model.ContentPage
import io.github.landwarderer.futon.mihon.parsers.model.ContentRating
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentState
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag

/**
 * Convert Mihon SManga to Domain Content.
 */
fun SManga.toDomainContent(
    source: MihonMangaSource,
    chapters: List<ContentChapter>? = null,
    publicUrl: String = "",
): Content {
    // Get baseUrl from source if available to resolve relative URLs
    val baseUrl = (source.catalogueSource as? HttpSource)?.baseUrl ?: ""
    
    val safeUrl = try { url } catch (e: UninitializedPropertyAccessException) { "" }
    val safeThumbnail = try { thumbnail_url } catch (e: UninitializedPropertyAccessException) { null }
    val absoluteThumbnailUrl = resolveUrl(baseUrl, safeThumbnail)
    val absolutePublicUrl = resolveUrl(baseUrl, safeUrl) ?: safeUrl
    
    // Safely access lateinit properties
    val safeTitle = try { title } catch (e: UninitializedPropertyAccessException) { null }
    val safeGenres = try { getGenres() } catch (e: UninitializedPropertyAccessException) { null }
    val safeAuthor = try { author } catch (e: UninitializedPropertyAccessException) { null }
    val safeArtist = try { artist } catch (e: UninitializedPropertyAccessException) { null }
    val safeDescription = try { description } catch (e: UninitializedPropertyAccessException) { null }
    val safeStatus = try { status } catch (e: UninitializedPropertyAccessException) { SManga.UNKNOWN }
    
    android.util.Log.i("MihonDataConverters", "toDomainContent: title='$safeTitle' url='$safeUrl' -> absoluteThumbnail='$absoluteThumbnailUrl'")
    
    return Content(
        id = generateContentId(safeUrl, source.name),
        title = safeTitle ?: "Unknown",
        altTitles = emptySet(),
        url = safeUrl,
        Url = publicUrl.ifBlank { absolutePublicUrl },
        rating = 0.0f,
        contentRating = run<ContentRating?> {
            val safeTags = setOf("safe", "all ages", "non-h", "sfw", "非h", "正常向", "全年龄", "全年龄向")
            val isExplicitlySafe = safeGenres?.any { it.lowercase() in safeTags } == true
            
            val adultGenres = setOf("adult", "hentai", "18+", "nsfw", "mature", "ecchi")
            val isContentNsfw = (!isExplicitlySafe && source.isNsfw) || safeGenres?.any { it.lowercase() in adultGenres } == true
            
            if (isExplicitlySafe) {
                ContentRating.SAFE
            } else if (isContentNsfw) {
                ContentRating.ADULT
            } else {
                null
            }
        },
        coverUrl = absoluteThumbnailUrl,
        largeCoverUrl = absoluteThumbnailUrl, // Also set largeCoverUrl for details page
        tags = safeGenres?.map { genreName: String ->
            ContentTag(
                title = genreName,
                key = genreName.lowercase().replace(" ", "_"),
                source = source,
            )
        }?.toSet() ?: emptySet(),
        state = when (safeStatus) {
            SManga.ONGOING -> ContentState.ONGOING
            SManga.COMPLETED -> ContentState.FINISHED
            SManga.ON_HIATUS -> ContentState.PAUSED
            SManga.CANCELLED -> ContentState.ABANDONED
            SManga.LICENSED -> ContentState.RESTRICTED
            SManga.PUBLISHING_FINISHED -> ContentState.FINISHED
            else -> ContentState.ONGOING
        },
        authors = buildSet {
            safeAuthor?.takeIf { it.isNotBlank() }?.let { add(it) }
            safeArtist?.takeIf { it.isNotBlank() && it != safeAuthor }?.let { add(it) }
        },
        description = safeDescription,
        chapters = chapters,
        source = source,
    )
}

/**
 * Convert app Content to Mihon SManga (for calling Mihon APIs).
 */
fun Content.toMihonManga(): SManga {
    // Get baseUrl from source if available
    val baseUrl = (source as? MihonMangaSource)?.let { mihonSource ->
        (mihonSource.catalogueSource as? HttpSource)?.baseUrl ?: ""
    } ?: ""
    
    var cleanUrl = url
    
    // Check if URL has duplicate protocol/baseUrl (e.g., "https://domain.comhttps//domain.com/path")
    // Look for embedded "http" that's not at the start
    val httpIndex = cleanUrl.indexOf("http", startIndex = 1)
    if (httpIndex > 0) {
        // Extract everything from the second "http" onwards
        cleanUrl = cleanUrl.substring(httpIndex)
        android.util.Log.w("MihonDataConverters", "Detected duplicate baseUrl, extracting: '$url' -> '$cleanUrl'")
    }
    
    // Fix malformed protocols (https// -> https://)
    cleanUrl = cleanUrl.replace(Regex("^(https?)/+"), "$1://")
    
    // If URL is absolute and starts with baseUrl, strip it to avoid duplicates in HttpSource
    if (baseUrl.isNotBlank()) {
        val baseHost = baseUrl.trimEnd('/')
        if (cleanUrl.startsWith(baseHost)) {
            val stripped = cleanUrl.substring(baseHost.length)
            if (stripped.startsWith("/") || stripped.isEmpty()) {
                cleanUrl = stripped
                android.util.Log.d("MihonDataConverters", "Stripped baseUrl from absolute URL: '$url' -> '$cleanUrl'")
            }
        }
    }
    
    // If URL still doesn't look absolute, log warning
    if (!cleanUrl.matches(Regex("^https?://.*")) && !cleanUrl.startsWith("/")) {
        android.util.Log.w("MihonDataConverters", "URL may be invalid after cleanup: '$cleanUrl' (original: '$url')")
    }
    
    // NOTE: Do NOT add a leading slash to non-absolute URLs.
    // Some extensions (e.g., zaimanhua) use pure IDs like "84652" which are then
    // internally combined with their API path. Adding a slash would cause
    // double-slash issues like "detail//84652" instead of "detail/84652".
    
    android.util.Log.d("MihonDataConverters", "toMihonManga: original='$url' cleaned='$cleanUrl'")
    
    return SManga.create().apply {
        this.url = cleanUrl
        this.title = this@toMihonManga.title
        this.author = this@toMihonManga.authors.firstOrNull()
        this.artist = this@toMihonManga.authors.drop(1).firstOrNull()
        this.description = this@toMihonManga.description
        this.genre = this@toMihonManga.tags.joinToString(", ") { it.title }
        this.status = when (this@toMihonManga.state) {
            ContentState.ONGOING -> SManga.ONGOING
            ContentState.FINISHED -> SManga.COMPLETED
            ContentState.PAUSED -> SManga.ON_HIATUS
            ContentState.ABANDONED -> SManga.CANCELLED
            ContentState.RESTRICTED -> SManga.LICENSED
            ContentState.UPCOMING -> SManga.UNKNOWN
            null -> SManga.UNKNOWN
        }
        this.thumbnail_url = this@toMihonManga.coverUrl
        this.initialized = true
    }
}

// ============ SChapter <-> ContentChapter ============

/**
 * Convert Mihon SChapter to App ContentChapter.
 */
fun SChapter.toContentChapter(source: ContentSource, overrideNumber: Float? = null): ContentChapter {
    val chapterId = generateChapterId(url, source.name)
    val finalNumber = overrideNumber ?: (if (chapter_number >= 0) chapter_number else 0f)
    
    android.util.Log.d("MihonDataConverters", "toContentChapter: name='$name' url='$url' -> id=$chapterId number=$finalNumber")
    
    return ContentChapter(
        id = chapterId,
        title = name.takeIf { it.isNotBlank() },
        number = finalNumber,
        volume = 0, // Mihon doesn't have volume numbers in SChapter
        url = url,
        scanlator = scanlator,
        uploadDate = date_upload,
        branch = scanlator, // Use scanlator as branch for grouping
        source = source,
    )
}

/**
 * Convert Apps ContentChapter to Mihon SChapter.
 */
fun ContentChapter.toMihonChapter(): SChapter {
    return SChapter.create().apply {
        this.url = this@toMihonChapter.url
        this.name = this@toMihonChapter.title ?: "Chapter ${this@toMihonChapter.number}"
        this.chapter_number = this@toMihonChapter.number
        this.date_upload = this@toMihonChapter.uploadDate
        this.scanlator = this@toMihonChapter.scanlator
    }
}

// ============ Page <-> ContentPage ============

/**
 * Convert Mihon Page to App's ContentPage.
 * 
 * NOTE: The chapter parameter is needed to generate unique page IDs.
 * Without it, all chapters would have pages with IDs 0, 1, 2... which causes
 * cache conflicts in the reader.
 */
fun Page.asContentPage(
    source: ContentSource,
    chapter: SChapter,
    headers: Map<String, String> = emptyMap()
): ContentPage {
    // Generate a unique page ID by combining chapter URL and page index
    // This prevents cache collisions between pages from different chapters
    val pageId = "${chapter.url}|page|$index".hashCode().toLong() and Long.MAX_VALUE
    
    return ContentPage(
        id = pageId,
        url = imageUrl ?: url,
        preview = null,
        headers = headers,
        source = source,
    )
}

/**
 * Convert App's ContentPage to Mihon Page.
 */
fun ContentPage.toMihonPage(): Page {
    return Page(
        index = id.toInt(),
        url = url,
        imageUrl = url.takeIf { it.isNotBlank() },
    )
}

// ============ ID Generation ============

/**
 * Generate a stable ID for a manga based on URL and source.
 */
private fun generateContentId(url: String, sourceName: String): Long {
    return "$sourceName|$url".hashCode().toLong() and Long.MAX_VALUE
}

/**
 * Generate a stable ID for a chapter based on URL and source.
 */
private fun generateChapterId(url: String, sourceName: String): Long {
    return "$sourceName|chapter|$url".hashCode().toLong() and Long.MAX_VALUE
}

// ============ URL Helpers ============

/**
 * Get the public URL for a manga from an HttpSource.
 */
fun HttpSource.getPublicContentUrl(manga: SManga): String {
    return try {
        getMangaUrl(manga)
    } catch (e: Exception) {
        ""
    }
}

/**
 * Get the public URL for a chapter from an HttpSource.
 */
fun HttpSource.getPublicChapterUrl(chapter: SChapter): String {
    return try {
        getChapterUrl(chapter)
    } catch (e: Exception) {
        ""
    }
}
/**
 * Resolve relative URL using baseUrl.
 */
private fun resolveUrl(baseUrl: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    
    if (baseUrl.isNotBlank()) {
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
    return url
}
