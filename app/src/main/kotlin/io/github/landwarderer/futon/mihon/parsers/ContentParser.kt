package io.github.landwarderer.futon.mihon.parsers

import io.github.landwarderer.futon.mihon.parsers.config.ConfigKey
import io.github.landwarderer.futon.mihon.parsers.config.ContentSourceConfig
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilterCapabilities
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilterOptions
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.Favicons
import io.github.landwarderer.futon.mihon.parsers.model.NovelChapterContent
import io.github.landwarderer.futon.mihon.parsers.model.SortOrder
import io.github.landwarderer.futon.mihon.parsers.model.search.ContentSearchQuery
import io.github.landwarderer.futon.mihon.parsers.model.search.ContentSearchQueryCapabilities
import io.github.landwarderer.futon.mihon.parsers.util.LinkResolver
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentPage
import java.util.*

interface ContentParser : Interceptor {

	val source: ContentSource

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	val availableSortOrders: Set<SortOrder>

	@Deprecated("Too complex. Use filterCapabilities instead")
	val searchQueryCapabilities: ContentSearchQueryCapabilities

	val filterCapabilities: ContentListFilterCapabilities

	val config: ContentSourceConfig

	val authorizationProvider: ContentParserAuthProvider?
		get() = this as? ContentParserAuthProvider

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [domain] instead.
	 */
	val configKeyDomain: ConfigKey.Domain

	val domain: String

	@Deprecated("Too complex. Use getList with filter instead")
	suspend fun getList(query: ContentSearchQuery): List<Content>

	suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content>

	/**
	 * Parse details for [Content]: chapters list, description, large cover, etc.
	 * Must return the same content, may change any fields excepts id, url and source
	 * @see Content.copy
	 */
	suspend fun getDetails(manga: Content): Content

	/**
	 * Parse pages list for specified chapter.
	 * @see ContentPage for details
	 */
	suspend fun getPages(chapter: ContentChapter): List<ContentPage>

	/**
	 * Fetch direct link to the page image.
	 */
	suspend fun getPageUrl(page: ContentPage): String

	/**
	 * Optional: Returns the complete HTML and image resources for the novel chapter for offline download. Defaults to null.
	 */
	suspend fun getChapterContent(chapter: ContentChapter): NovelChapterContent? = null

	suspend fun getFilterOptions(): ContentListFilterOptions

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	suspend fun getFavicons(): Favicons

	fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>)

	suspend fun getRelatedContent(seed: Content): List<Content>

	fun getRequestHeaders(): Headers

	@InternalParsersApi
	suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Content?
}
