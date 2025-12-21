package io.github.landwarderer.futon.local.ui

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.core.ui.CoroutineIntentService
import io.github.landwarderer.futon.local.data.index.LocalMangaIndex
import javax.inject.Inject

@AndroidEntryPoint
class LocalIndexUpdateService : CoroutineIntentService() {

	@Inject
	lateinit var localMangaIndex: LocalMangaIndex

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		localMangaIndex.update()
	}

	override fun IntentJobContext.onError(error: Throwable) = Unit
}
