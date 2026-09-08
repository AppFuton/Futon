package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.reader.ui.pager.ReaderPage

/** Select by chapter identity: appending pages can also trim the start of the snapshot. */
internal fun List<ReaderPage>.nextChapterPrefetchPages(currentChapterId: Long, limit: Int): List<ReaderPage> {
    if (limit <= 0) return emptyList()
    val boundary = indexOfLast { it.chapterId == currentChapterId }
    if (boundary < 0) return emptyList()
    val first = boundary + 1
    val nextChapterId = getOrNull(first)?.chapterId ?: return emptyList()
    var end = first
    while (end < size && end - first < limit && this[end].chapterId == nextChapterId) {
        end++
    }
    return subList(first, end)
}
