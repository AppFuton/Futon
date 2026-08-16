package okhttp3

/**
 * Stub class for OkHttp CompressionInterceptor to provide binary compatibility
 * with Mihon extensions compiled against okhttp-brotli / okhttp-zstd / Keiyoushi v1.6+.
 */
class CompressionInterceptor(
    val algorithms: List<Any> = emptyList(),
) : Interceptor {
    constructor(vararg algorithms: Any) : this(algorithms.toList())

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
