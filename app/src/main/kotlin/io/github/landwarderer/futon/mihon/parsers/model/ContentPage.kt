package io.github.landwarderer.futon.mihon.parsers.model


data class ContentPage(
	/**
	 * Unique identifier for page
	 */
	@JvmField val id: Long,
	/**
	 * Relative url to page (**without** a domain) or any other uri.
	 * Used principally in parsers.
	 * May contain link to image or html page.
	 * @see ContentParser.getPageUrl
	 */
	@JvmField val url: String,
	/**
	 * Absolute url of the small page image if exists, null otherwise
	 */
	@JvmField val preview: String?,
	/**
	 * Optional per-page request headers (e.g., Referer) to be applied when fetching the page/image.
	 */
	@JvmField val headers: Map<String, String>? = null,
	@JvmField val source: ContentSource,
)

@Deprecated("Use id instead of index", ReplaceWith("ContentPage(index.toLong(), url, previewUrl, source)"))
fun ContentPage(index: Int, url: String, previewUrl: String?, source: ContentSource): ContentPage = ContentPage(
	id = index.toLong(),
	url = url,
	preview = previewUrl,
	source = source,
)
