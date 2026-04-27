package eu.kanade.tachiyomi.network

/**
 * Progress listener interface for tracking download progress.
 */
interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
