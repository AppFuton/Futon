package io.github.landwarderer.futon.reader.domain

import io.github.landwarderer.futon.core.model.MangaHistory
import io.github.landwarderer.futon.core.prefs.ChapterCompletionMode
import io.github.landwarderer.futon.reader.ui.ReaderState
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

/** One decision per reader opening, using history captured before any startup writes or recovery. */
internal class SmartResumeResolver(
    private val originalState: ReaderState,
    private val history: MangaHistory?,
    private val rule: ChapterCompletionRule,
    private val waitForUpdates: Boolean,
) {
    private var resolvedState: ReaderState? = null

    suspend fun resolve(
        branchChapterIds: List<Long>,
        savedPageCount: Int,
        updatesFinished: Boolean,
        loadNext: suspend (Long) -> Boolean,
    ): ReaderState? {
        resolvedState?.let { return it }
        val saved = history
        val index = branchChapterIds.indexOf(originalState.chapterId)
        val candidate = saved != null && saved.chapterId == originalState.chapterId &&
            saved.chaptersCount > 0 && index == saved.chaptersCount - 1 &&
            rule.isCompleted(saved.page, savedPageCount)
        if (!candidate) return finish(originalState)
        if (waitForUpdates && !updatesFinished) return null
        val nextId = branchChapterIds.getOrNull(index + 1) ?: return finish(originalState)
        val loaded = runCatchingCancellable { loadNext(nextId) }.getOrDefault(false)
        return finish(if (loaded) ReaderState(nextId, 0, 0) else originalState)
    }

    private fun finish(state: ReaderState): ReaderState {
        resolvedState = state
        return state
    }

    fun fallback(): ReaderState = resolvedState ?: finish(originalState)
}
