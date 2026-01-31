package eu.kanade.tachiyomi.source

/**
 * Factory interface for sources that provide multiple catalogues.
 * This is a stub implementation - extensions will provide the actual implementation.
 */
interface SourceFactory {
	/**
	 * Create all sources provided by this factory.
	 */
	fun createSources(): List<Source>
}
