package io.github.landwarderer.futon.mihon.parsers.core

import androidx.annotation.CallSuper
import io.github.landwarderer.futon.mihon.parsers.ContentLoaderContext
import io.github.landwarderer.futon.mihon.parsers.ContentParser
import io.github.landwarderer.futon.mihon.parsers.InternalParsersApi
import io.github.landwarderer.futon.mihon.parsers.config.ConfigKey
import io.github.landwarderer.futon.mihon.parsers.config.ContentSourceConfig
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentPage
import io.github.landwarderer.futon.mihon.parsers.model.ContentRating
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentType
import io.github.landwarderer.futon.mihon.parsers.model.Favicons
import io.github.landwarderer.futon.mihon.parsers.model.SortOrder
import io.github.landwarderer.futon.mihon.parsers.model.search.ContentSearchQuery
import io.github.landwarderer.futon.mihon.parsers.model.search.ContentSearchQueryCapabilities
import io.github.landwarderer.futon.mihon.parsers.network.OkHttpWebClient
import io.github.landwarderer.futon.mihon.parsers.network.WebClient
import io.github.landwarderer.futon.mihon.parsers.util.FaviconParser
import io.github.landwarderer.futon.mihon.parsers.util.LinkResolver
import io.github.landwarderer.futon.mihon.parsers.util.RelatedContentFinder
import io.github.landwarderer.futon.mihon.parsers.util.convertToContentListFilter
import io.github.landwarderer.futon.mihon.parsers.util.toAbsoluteUrl
import io.github.landwarderer.futon.mihon.parsers.util.toContentSearchQueryCapabilities
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

@Suppress("OVERRIDE_DEPRECATION")
@InternalParsersApi
public abstract class AbstractContentParser @InternalParsersApi constructor(
    @property:InternalParsersApi public val context: ContentLoaderContext,
    public final override val source: ContentSource,
) : ContentParser {

	public final override val searchQueryCapabilities: ContentSearchQueryCapabilities
		get() = filterCapabilities.toContentSearchQueryCapabilities()

	public override val config: ContentSourceConfig by lazy { context.getConfig(source) }

	public open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	protected val sourceContentRating: ContentRating?
		get() = if (source.contentType == ContentType.HENTAI_MANGA) {
			ContentRating.ADULT
		} else {
			null
		}

	protected val isNsfwSource: Boolean = source.contentType == ContentType.HENTAI_MANGA

	protected open val userAgentKey: ConfigKey.UserAgent = try {
		ConfigKey.UserAgent(context.getDefaultUserAgent())
	} catch (_: NoSuchMethodError) {
		ConfigKey.UserAgent(io.github.landwarderer.futon.mihon.parsers.network.UserAgents.CHROME_MOBILE)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.build()

	/**
	 * Used as fallback if value of `order` passed to [getList] is null
	 */
	public open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	final override val domain: String
		get() = config[configKeyDomain]

	@JvmField
	protected val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Search list of manga by specified searchQuery
	 *
	 * @param query searchQuery
	 */
	public override suspend fun getList(query: ContentSearchQuery): List<Content> = getList(
		offset = query.offset,
		order = query.order ?: defaultSortOrder,
		filter = convertToContentListFilter(query),
	)

	/**
	 * Fetch direct link to the page image.
	 */
	public override suspend fun getPageUrl(page: ContentPage): String = page.url.toAbsoluteUrl(domain)

	protected open val faviconDomain: String
		get() = domain

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	public override suspend fun getFavicons(): Favicons {
		return FaviconParser(webClient, faviconDomain).parseFavicons()
	}

	@CallSuper
	public override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	public override suspend fun getRelatedContent(seed: Content): List<Content> {
		return RelatedContentFinder(listOf(this)).invoke(seed)
	}

	/**
	 * Return [Content] object by web link to it
	 * @see [Content.publicUrl]
	 */
	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Content? = null

	override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}

