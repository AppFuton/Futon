package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Mihon-compatible Page class.
 * Ported from Mihon source-api for extension compatibility.
 * 
 * Includes [uri] and [ProgressListener] for binary compatibility with extensions.
 */
@Serializable
open class Page @JvmOverloads constructor(
    var index: Int,
    var url: String = "",
    var imageUrl: String? = null,
    @Transient var uri: Uri? = null,
) : ProgressListener {

    val number: Int
        get() = index + 1

    @Transient
    var status: State = State.Queue

    @Transient
    var progress: Int = 0

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    fun copy(
        index: Int = this.index,
        url: String = this.url,
        imageUrl: String? = this.imageUrl,
    ): Page = Page(index, url, imageUrl)

    sealed interface State {
        data object Queue : State
        data object LoadPage : State
        data object DownloadImage : State
        data object Ready : State
        data class Error(val error: Throwable) : State
    }
}
