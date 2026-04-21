package io.github.landwarderer.futon.mihon

import android.util.Log
import dalvik.system.PathClassLoader

/**
 * A ClassLoader that loads classes from its own path before delegating to its parent.
 * 
 * This is necessary for Mihon extensions because they may bundle different versions
 * of libraries than App uses, and we need to isolate them.
 */
class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    /**
     * List of packages that should always be loaded from the parent ClassLoader.
     * These are core Android/Kotlin classes and Mihon API classes that must be shared.
     */
    private val parentPackages = setOf(
        "java.",
        "javax.",
        "kotlin.",
        "kotlinx.",
        "android.",
        "androidx.",
        "org.json.",
        "org.jsoup.",
        "okhttp3.",
        "okio.",
        "rx.",
        "eu.kanade.tachiyomi.source.",
        "eu.kanade.tachiyomi.network.",
        "eu.kanade.tachiyomi.util.",
        "uy.kohesive.injekt.",
        "ireader.core.",
        "io.ktor.",
        "com.fleeksoft.",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Check if we should delegate to parent immediately
        if (parentPackages.any { name.startsWith(it) }) {
            try {
                return parent.loadClass(name)
            } catch (e: ClassNotFoundException) {
                // fall through to child loading
            }
        }

        // Try to find the class in our own path first
        return try {
            findLoadedClass(name) ?: findClass(name)
        } catch (e: ClassNotFoundException) {
            // Fall back to parent ClassLoader
            try {
                parent.loadClass(name)
            } catch (e2: ClassNotFoundException) {
                if (name.contains("tachiyomi")) {
                    Log.w("ChildFirstLoader", "Class not found: $name")
                }
                throw e2
            }
        }
    }
}
