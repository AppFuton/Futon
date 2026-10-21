package io.github.landwarderer.futon.novel.data.source

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource

enum class NovelParserSource(
	val title: String,
	val domain: String,
	val defaultUrl: String,
	val locale: String = "en",
	val contentType: ContentType = ContentType.NOVEL,
) : MangaSource {
	FREEWEBNOVEL(
		title = "FreeWebNovel",
		domain = "freewebnovel.com",
		defaultUrl = "https://freewebnovel.com",
	),
	LNORI(
		title = "Lnori",
		domain = "lnori.com",
		defaultUrl = "https://lnori.com",
	),
	ROYALROAD(
		title = "Royal Road",
		domain = "royalroad.com",
		defaultUrl = "https://www.royalroad.com",
	),
	SCRIBBLEHUB(
		title = "Scribble Hub",
		domain = "scribblehub.com",
		defaultUrl = "https://www.scribblehub.com",
	),
	RANOBES(
		title = "Ranobes",
		domain = "ranobes.net",
		defaultUrl = "https://ranobes.net",
	),
	WUXIAWORLD(
		title = "Wuxiaworld",
		domain = "wuxiaworld.com",
		defaultUrl = "https://www.wuxiaworld.com",
	),
	LNCRAWLER(
		title = "LNCrawler",
		domain = "lncrawler.monster",
		defaultUrl = "https://lncrawler.monster",
	);

	companion object {
		fun fromName(name: String?): NovelParserSource? {
			if (name == null) return null
			return entries.find { it.name.equals(name, ignoreCase = true) }
		}
	}
}
