package io.github.landwarderer.futon.settings.about.changelog

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.jsoup.internal.StringUtil
import io.github.landwarderer.futon.core.github.AppUpdateRepository
import io.github.landwarderer.futon.core.ui.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class ChangelogViewModel @Inject constructor(
	private val appUpdateRepository: AppUpdateRepository,
) : BaseViewModel() {

	val changelog = MutableStateFlow<String?>(null)

	init {
		launchLoadingJob(Dispatchers.IO) {
			val version = appUpdateRepository.fetchUpdate()
			changelog.value = version?.let { "# ${it.name}\n\n${it.description}" }
		}
	}
}
