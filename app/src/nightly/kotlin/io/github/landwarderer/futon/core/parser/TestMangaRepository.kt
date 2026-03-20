package io.github.landwarderer.futon.core.parser

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.model.TestMangaSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext

@Suppress("unused")
class TestMangaRepository(
	private val loaderContext: MangaLoaderContext,
	cache: MemoryContentCache
) : EmptyMangaRepository(TestMangaSource)
