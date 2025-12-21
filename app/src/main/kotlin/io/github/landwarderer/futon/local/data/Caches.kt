package io.github.landwarderer.futon.local.data

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PageCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FaviconCache
