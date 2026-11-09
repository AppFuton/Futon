package io.github.landwarderer.futon.novel.ui.reader

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.net.URI

/** Normalizes parser output while preserving markup and resolving chapter-relative resources. */
internal object NovelChapterContent {
	private val htmlTag = Regex("(?is)<!doctype\\s+html\\b|</?[a-z][\\w:-]*(?:\\s[^<>]*?)?/?>")
	private val resourceUrl = Regex("(?i)url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)")

	private val contentSafelist: Safelist = Safelist.relaxed()
		.addTags(
			"hr", "ruby", "rt", "rp", "s", "del", "ins", "mark",
			"figure", "figcaption", "section", "article",
		)
		.addAttributes("img", "src", "data-src", "data-original", "srcset", "alt", "title", "width", "height")
		.addAttributes("a", "href", "title", "target")
		.addAttributes(":all", "class", "style", "id", "dir")
		.addProtocols("img", "src", "http", "https", "data")
		.addProtocols("img", "data-src", "http", "https", "data")
		.addProtocols("img", "data-original", "http", "https", "data")
		.addProtocols("a", "href", "http", "https", "mailto")
		.preserveRelativeLinks(true)

	fun toHtmlFragment(content: String, baseUrl: String? = null): String {
		val trimmed = content.trim().removePrefix("\uFEFF")
		if (trimmed.isEmpty()) return trimmed

		val fragment = if (htmlTag.containsMatchIn(trimmed)) {
			trimmed
		} else {
			trimmed
				.split(Regex("\\r?\\n\\s*\\r?\\n"))
				.joinToString("") { paragraph ->
					"<p>${escapeHtml(paragraph.trim()).replace(Regex("\\r?\\n"), "<br>")}</p>"
				}
		}
		val resolved = resolveResources(fragment, baseUrl)
		return Jsoup.clean(resolved, baseUrl.orEmpty(), contentSafelist)
	}

	private fun resolveResources(html: String, baseUrl: String?): String {
		if (baseUrl.isNullOrBlank()) return html

		val document = Jsoup.parseBodyFragment(html, baseUrl)
		document.select("[src], [href], [poster], [data-src], [data-original], [srcset]").forEach { element ->
			for (attribute in listOf("src", "href", "poster", "data-src", "data-original", "srcset")) {
				if (!element.hasAttr(attribute)) continue
				val value = element.attr(attribute)
				element.attr(attribute, if (attribute == "srcset") {
					value.split(',').joinToString(",") { candidate ->
						val parts = candidate.trim().split(Regex("\\s+"), limit = 2)
						resolveUrl(parts.first(), baseUrl) + parts.getOrNull(1)?.let { " $it" }.orEmpty()
					}
				} else {
					resolveUrl(value, baseUrl)
				})
			}
		}
		document.select("[style], style").forEach { element ->
			val attribute = if (element.hasAttr("style")) "style" else ""
			val css = if (attribute.isEmpty()) element.data() else element.attr(attribute)
			val resolvedCss = resourceUrl.replace(css) { match ->
				val rawUrl = match.groupValues[2]
				"url(${match.groupValues[1]}${resolveUrl(rawUrl, baseUrl)}${match.groupValues[1]})"
			}
			if (attribute.isEmpty()) element.html(resolvedCss) else element.attr(attribute, resolvedCss)
		}
		return document.body().html()
	}

	private fun resolveUrl(value: String, baseUrl: String): String {
		val trimmed = value.trim()
		if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//") ||
			Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
		) return trimmed
		return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
	}

	private fun escapeHtml(text: String): String = text
		.replace("&", "&amp;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
		.replace("\"", "&quot;")
		.replace("'", "&#39;")
}
