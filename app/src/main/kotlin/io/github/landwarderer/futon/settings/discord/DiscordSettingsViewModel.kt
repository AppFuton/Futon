package io.github.landwarderer.futon.settings.discord

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.isNetworkError
import io.github.landwarderer.futon.parsers.util.runCatchingCancellable
import io.github.landwarderer.futon.scrobbling.discord.data.DiscordRepository
import javax.inject.Inject

@HiltViewModel
class DiscordSettingsViewModel @Inject constructor(
	private val settings: AppSettings,
	private val repository: DiscordRepository,
) : BaseViewModel() {

	val tokenState: StateFlow<Pair<TokenState, String?>> = settings.observe(
		AppSettings.KEY_DISCORD_RPC,
		AppSettings.KEY_DISCORD_TOKEN,
	).flatMapLatest {
		checkToken()
	}.stateIn(
		viewModelScope + Dispatchers.IO,
		SharingStarted.Eagerly,
		TokenState.CHECKING to settings.discordToken,
	)

	private fun checkToken(): Flow<Pair<TokenState, String?>> = flow {
		val token = settings.discordToken
		if (!settings.isDiscordRpcEnabled) {
			emit(
				if (token == null) {
					TokenState.EMPTY to null
				} else {
					TokenState.VALID to token
				},
			)
			return@flow
		}
		if (token == null) {
			emit(TokenState.REQUIRED to null)
			return@flow
		}
		emit(TokenState.CHECKING to token)
		if (validateToken(token)) {
			emit(TokenState.VALID to token)
		} else {
			emit(TokenState.INVALID to token)
		}
	}

	private suspend fun validateToken(token: String) = runCatchingCancellable {
		repository.checkToken(token)
	}.fold(
		onSuccess = { true },
		onFailure = { it.isNetworkError() },
	)
}
