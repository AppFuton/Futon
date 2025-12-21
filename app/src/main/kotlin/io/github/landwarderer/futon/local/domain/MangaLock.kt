package io.github.landwarderer.futon.local.domain

import io.github.landwarderer.futon.core.util.MultiMutex
import io.github.landwarderer.futon.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLock @Inject constructor() : MultiMutex<Manga>()
