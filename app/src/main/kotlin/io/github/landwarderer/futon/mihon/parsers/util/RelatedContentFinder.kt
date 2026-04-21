package io.github.landwarderer.futon.mihon.parsers.util

import kotlinx.coroutines.*
import io.github.landwarderer.futon.mihon.parsers.ContentParser
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.SortOrder

public class RelatedContentFinder(
    private val parsers: Collection<ContentParser>,
) {

    public suspend operator fun invoke(seed: Content): List<Content> = withContext(Dispatchers.Default) {
        coroutineScope {
            parsers.singleOrNull()?.let { parser ->
                findRelatedImpl(this, parser, seed)
            } ?: parsers.map { parser ->
                async {
                    findRelatedImpl(this, parser, seed)
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun findRelatedImpl(scope: CoroutineScope, parser: ContentParser, seed: Content): List<Content> {
        val words = HashSet<String>()
        words += seed.title.splitByWhitespace()
        seed.altTitles.forEach {
            words += it.splitByWhitespace()
        }
        if (words.isEmpty()) {
            return emptyList()
        }
        
        // æ—¥å¿—ï¼šè®°å½• seed çš„ ID å’Œ URL
        println("[RelatedContentFinder] seed.id=${seed.id} seed.url='${seed.url}' seed.title='${seed.title}'")
        
        val results = words.map { keyword ->
            scope.async {
                try {
                    val result = parser.getList(
                        0,
                        if (SortOrder.RELEVANCE in parser.availableSortOrders) {
                            SortOrder.RELEVANCE
                        } else {
                            parser.availableSortOrders.first()
                        },
                        ContentListFilter(
                            query = keyword,
                        ),
                    )
                    
                    // æ—¥å¿—ï¼šè®°å½•æœç´¢ç»“æžœå’Œè¿‡æ»¤æƒ…å†µ
                    result.forEach { manga ->
                        val willFilter = manga.id == seed.id
                        println("[RelatedContentFinder] result: id=${manga.id} url='${manga.url}' title='${manga.title}' willFilterAsSeed=$willFilter")
                    }
                    
                    result.filter { it.id != seed.id && it.containKeyword(keyword) }
                } catch (e: Exception) {
                    emptyList<Content>()
                }
            }
        }.awaitAll()
        return results.minBy { if (it.isEmpty()) Int.MAX_VALUE else it.size }
    }


    private fun Content.containKeyword(keyword: String): Boolean {
        return title.contains(keyword, ignoreCase = true)
            || altTitles.any { it.contains(keyword, ignoreCase = true) }
    }
}

