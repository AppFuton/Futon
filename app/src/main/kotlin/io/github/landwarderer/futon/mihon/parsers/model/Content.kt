package io.github.landwarderer.futon.mihon.parsers.model

import androidx.collection.ArrayMap
import io.github.landwarderer.futon.mihon.parsers.util.findById
import io.github.landwarderer.futon.mihon.parsers.util.nullIfEmpty

data class Content(
    /**
     * Unique identifier for manga
     */
    @JvmField val id: Long,
    /**
     * Content title, human-readable
     */
    @JvmField val title: String,
    /**
     * Alternative titles (for example on other language), may be empty
     */
    @JvmField val altTitles: Set<String>,
    /**
     * Relative url to manga (**without** a domain) or any other uri.
     * Used principally in parsers
     */
    @JvmField val url: String,
    /**
     * Absolute url to manga, must be ready to open in browser
     */
    @JvmField val Url: String,
    /**
     * Normalized manga rating, must be in range of 0..1 or [RATING_UNKNOWN] if rating s unknown
     * @see hasRating
     */
    @JvmField val rating: Float,
    /**
     * Indicates that manga may contain sensitive information (18+, NSFW)
     */
    @JvmField val contentRating: ContentRating?,
    /**
     * Absolute link to the cover
     * @see largeCoverUrl
     */
    @JvmField val coverUrl: String?,
    /**
     * Tags (genres) of the manga
     */
    @JvmField val tags: Set<ContentTag>,
    /**
     * Content status (ongoing, finished) or null if unknown
     */
    @JvmField val state: ContentState?,
    /**
     * Authors of the manga
     */
    @JvmField val authors: Set<String>,
    /**
     * Large cover url (absolute), null if is no large cover
     * @see coverUrl
     */
    @JvmField val largeCoverUrl: String? = null,
    /**
     * Content description, may be html or null
     */
    @JvmField val description: String? = null,
    /**
     * List of chapters
     */
    @JvmField val chapters: List<ContentChapter>? = null,
    /**
     * Content source
     */
    @JvmField val source: ContentSource,
) {

    @Deprecated("Accepts rating as Int; use Float in range 0..1 instead")
    constructor(
        id: Long,
        title: String,
        altTitles: Set<String>,
        url: String,
        Url: String,
        rating: Int,
        contentRating: ContentRating?,
        coverUrl: String?,
        tags: Set<ContentTag>,
        state: ContentState?,
        authors: Set<String>,
        largeCoverUrl: String? = null,
        description: String? = null,
        chapters: List<ContentChapter>? = null,
        source: ContentSource,
    ) : this(
        id = id,
        title = title,
        altTitles = altTitles,
        url = url,
        Url = Url,
        rating = rating.toFloat(),
        contentRating = contentRating,
        coverUrl = coverUrl?.nullIfEmpty(),
        tags = tags,
        state = state,
        authors = authors,
        largeCoverUrl = largeCoverUrl?.nullIfEmpty(),
        description = description?.nullIfEmpty(),
        chapters = chapters,
        source = source,
    )

    @Deprecated("Use other constructor")
    constructor(
        /**
         * Unique identifier for manga
         */
        id: Long,
        /**
         * Content title, human-readable
         */
        title: String,
        /**
         * Alternative title (for example on other language), may be null
         */
        altTitle: String?,
        /**
         * Relative url to manga (**without** a domain) or any other uri.
         * Used principally in parsers
         */
        url: String,
        /**
         * Absolute url to manga, must be ready to open in browser
         */
        Url: String,
        /**
         * Normalized manga rating, must be in range of 0..1 or [RATING_UNKNOWN] if rating s unknown
         * @see hasRating
         */
        rating: Float,
        /**
         * Indicates that manga may contain sensitive information (18+, NSFW)
         */
        isNsfw: Boolean,
        /**
         * Absolute link to the cover
         * @see largeCoverUrl
         */
        coverUrl: String?,
        /**
         * Tags (genres) of the manga
         */
        tags: Set<ContentTag>,
        /**
         * Content status (ongoing, finished) or null if unknown
         */
        state: ContentState?,
        /**
         * Authors of the manga
         */
        author: String?,
        /**
         * Large cover url (absolute), null if is no large cover
         * @see coverUrl
         */
        largeCoverUrl: String? = null,
        /**
         * Content description, may be html or null
         */
        description: String? = null,
        /**
         * List of chapters
         */
        chapters: List<ContentChapter>? = null,
        /**
         * Content source
         */
        source: ContentSource,
    ) : this(
        id = id,
        title = title,
        altTitles = setOfNotNull(altTitle?.nullIfEmpty()),
        url = url,
        Url = Url,
        rating = rating,
        contentRating = if (isNsfw) ContentRating.ADULT else null,
        coverUrl = coverUrl?.nullIfEmpty(),
        tags = tags,
        state = state,
        authors = setOfNotNull(author),
        largeCoverUrl = largeCoverUrl?.nullIfEmpty(),
        description = description?.nullIfEmpty(),
        chapters = chapters,
        source = source,
    )

    /**
     * Author of the manga, may be null
     */
    @Deprecated("Please use authors")
    val author: String?
        get() = authors.firstOrNull()

    /**
     * Alternative title (for example on other language), may be null
     */
    @Deprecated("Please use altTitles")
    val altTitle: String?
        get() = altTitles.firstOrNull()

    /**
     * Return if manga has a specified rating
     * @see rating
     */
    val hasRating: Boolean
        get() = rating > 0f && rating <= 1f

    @Deprecated("Use contentRating instead", ReplaceWith("contentRating == ContentRating.ADULT"))
    val isNsfw: Boolean
        get() = contentRating == ContentRating.ADULT

    fun getChapters(branch: String?): List<ContentChapter> {
        return chapters?.filter { x -> x.branch == branch }.orEmpty()
    }

    fun findChapterById(id: Long): ContentChapter? = chapters?.findById(id)

    fun requireChapterById(id: Long): ContentChapter = findChapterById(id)
        ?: throw NoSuchElementException("Chapter with id $id not found")

    fun getBranches(): Map<String?, Int> {
        if (chapters.isNullOrEmpty()) {
            return emptyMap()
        }
        val result = ArrayMap<String?, Int>()
        chapters.forEach {
            val key = it.branch
            result[key] = result.getOrDefault(key, 0) + 1
        }
        return result
    }
}
