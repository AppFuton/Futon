package eu.kanade.tachiyomi.network

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers

/**
 * Util for evaluating JavaScript in sources.
 *
 * Uses QuickJS (with Rhino fallback) to execute JavaScript code.
 * This provides compatibility with Mihon extensions that use JavaScriptEngine.
 *
 * @since extensions-lib 1.4
 */
class JavaScriptEngine(private val context: Context) {

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T {
        return QuickJs.create(jobDispatcher = Dispatchers.Default).use { qjs ->
            qjs.maxStackSize = 1L shl 20  // 1MB
            qjs.memoryLimit = 64L shl 20   // 64MB soft limit
            val result = qjs.evaluate<Any?>(script)
            result as T
        }
    }
}
