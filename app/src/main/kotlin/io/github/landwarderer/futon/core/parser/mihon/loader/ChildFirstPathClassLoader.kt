package io.github.landwarderer.futon.core.parser.mihon.loader

import dalvik.system.PathClassLoader

/**
 * A ClassLoader that loads classes from its own path before delegating to its parent.
 *
 * This is necessary for Mihon extensions because they may bundle different versions
 * of libraries than Kototoro uses, and we need to isolate them.
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
            return parent.loadClass(name)
        }

        // Try to find the class in our own path first
        return try {
            findLoadedClass(name) ?: findClass(name)
        } catch (e: ClassNotFoundException) {
            // Fall back to parent ClassLoader
            parent.loadClass(name)
        }
    }
}
