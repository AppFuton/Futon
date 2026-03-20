package io.github.landwarderer.futon.settings.sources

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.MangaSource
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.parser.ParserMangaRepository
import io.github.landwarderer.futon.core.prefs.SourceSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.ui.util.ReversibleAction
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import javax.inject.Inject

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	private val cookieJar: MutableCookieJar,
	private val mangaSourcesRepository: MangaSourcesRepository,
) : BaseViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {

	val source = MangaSource(savedStateHandle.get<String>(AppRouter.KEY_SOURCE))
	val repository = mangaRepositoryFactory.create(source)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val username = MutableStateFlow<String?>(null)
	val isAuthorized = MutableStateFlow<Boolean?>(null)
	val browserUrl = MutableStateFlow<String?>(null)
	val isEnabled = mangaSourcesRepository.observeIsEnabled(source)
	private var usernameLoadJob: Job? = null

	init {
		when (repository) {
			is ParserMangaRepository -> {
				browserUrl.value = "https://${repository.domain}"
				repository.getConfig().subscribe(this)
				loadUsername(repository.getAuthProvider())
			}
		}
	}

	override fun onCleared() {
		when (repository) {
			is ParserMangaRepository -> {
				repository.getConfig().unsubscribe(this)
			}
		}
		super.onCleared()
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (repository is CachingMangaRepository) {
			if (key != SourceSettings.KEY_SLOWDOWN && key != SourceSettings.KEY_SORT_ORDER) {
				repository.invalidateCache()
			}
		}
		if (repository is ParserMangaRepository) {
			if (key == SourceSettings.KEY_DOMAIN) {
				browserUrl.value = "https://${repository.domain}"
			}
		}
	}

	fun onResume() {
		if (usernameLoadJob?.isActive != true && repository is ParserMangaRepository) {
			loadUsername(repository.getAuthProvider())
		}
	}

	fun clearCookies() {
		if (repository !is ParserMangaRepository) return
		launchLoadingJob(Dispatchers.IO) {
			val url = HttpUrl.Builder()
				.scheme("https")
				.host(repository.domain)
				.build()
			cookieJar.removeCookies(url, null)
			onActionDone.call(ReversibleAction(R.string.cookies_cleared, null))
			loadUsername(repository.getAuthProvider())
		}
	}

	fun setEnabled(value: Boolean) {
		launchJob(Dispatchers.IO) {
			mangaSourcesRepository.setSourcesEnabled(setOf(source), value)
		}
	}

	private fun loadUsername(authProvider: MangaParserAuthProvider?) {
		launchLoadingJob(Dispatchers.IO) {
			try {
				username.value = null
				isAuthorized.value = null
				isAuthorized.value = authProvider?.isAuthorized()
				username.value = authProvider?.getUsername()
			} catch (_: AuthRequiredException) {
			}
		}
	}
}
