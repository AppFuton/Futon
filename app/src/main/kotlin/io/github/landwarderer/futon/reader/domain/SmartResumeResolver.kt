package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.MangaHistory
import io.github.landwarderer.futon.core.model.findSameChapter
import io.github.landwarderer.futon.core.prefs.ChapterCompletionMode
import io.github.landwarderer.futon.reader.ui.ReaderState
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

internal data class ChapterCompletionRule(
    val mode: ChapterCompletionMode,
    val percentage: Int,
    val pagesRemaining: Int,
) {
    fun isCompleted(page: Int, pageCount: Int): Boolean {
        if (pageCount <= 0 || page !in 0 until pageCount) return false
        return when (mode) {
            ChapterCompletionMode.PERCENTAGE -> percentage in 1..100 &&
                (page.toLong() + 1) * 100 >= percentage.toLong() * pageCount
            ChapterCompletionMode.PAGES_REMAINING -> pagesRemaining >= 0 &&
                pageCount.toLong() - page - 1 <= pagesRemaining
        }
    }
}

/**
 * Finds the chapter the reader opened on in a newer chapter list. The source may have given it a new id since the
 * list the reader started from, so fall back to the same-chapter matching that history recovery uses.
 */
internal fun List<MangaChapter>.findResumeChapter(original: MangaChapter): MangaChapter? =
    findById(original.id) ?: findSameChapter(original)

/**
 * One decision per reader opening, using history captured before any startup writes or recovery.
 *
 * Only a finished chapter that was the last one at the reader's last save can move on. When the known list has no
 * chapter after it yet, the decision waits (returns null) for updated details. Every other start resolves at once.
 */
internal class SmartResumeResolver(
    private val originalState: ReaderState,
    private val history: MangaHistory?,
    private val rule: ChapterCompletionRule,
) {
    private var resolvedState: ReaderState? = null

    val isResolved: Boolean
        get() = resolvedState != null

    /**
     * @param chapterId the id of the opened chapter in [branchChapterIds]. It differs from the original id when the
     * source gave the chapter a new id since the reader started.
     */
    suspend fun resolve(
        branchChapterIds: List<Long>,
        savedPageCount: Int,
        updatesFinished: Boolean,
        chapterId: Long = originalState.chapterId,
        loadNext: suspend (Long) -> Boolean,
    ): ReaderState? {
        resolvedState?.let { return it }
        val saved = history
        val index = branchChapterIds.indexOf(chapterId)
        val candidate = saved != null && saved.chapterId == originalState.chapterId &&
            saved.chaptersCount > 0 && index == saved.chaptersCount - 1 &&
            rule.isCompleted(saved.page, savedPageCount)
        if (!candidate) return finish(originalState)
        val nextId = branchChapterIds.getOrNull(index + 1)
            ?: return if (updatesFinished) finish(originalState) else null
        val loaded = runCatchingCancellable { loadNext(nextId) }.getOrDefault(false)
        return finish(if (loaded) ReaderState(nextId, 0, 0) else originalState)
    }

    private fun finish(state: ReaderState): ReaderState {
        resolvedState = state
        return state
    }

    fun fallback(): ReaderState = resolvedState ?: finish(originalState)
}
