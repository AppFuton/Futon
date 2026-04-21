package io.github.landwarderer.futon.core.network

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MangaHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ContentHttpClient
