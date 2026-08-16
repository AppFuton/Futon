package okhttp3

import okhttp3.brotli.Brotli
import okhttp3.zstd.Zstd

/**
 * Stub object providing compression algorithm identifiers for OkHttp CompressionInterceptor.
 */
object CompressionAlgorithms {
    @JvmField
    val brotli: Any = Brotli

    @JvmField
    val zstd: Any = Zstd

    @JvmField
    val gzip: Any = "gzip"

    @JvmField
    val deflate: Any = "deflate"
}
