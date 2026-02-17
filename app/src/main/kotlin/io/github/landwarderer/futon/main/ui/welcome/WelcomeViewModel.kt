package io.github.landwarderer.futon.main.ui.welcome

import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.landwarderer.futon.core.LocalizedAppContext
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.LocaleComparator
import io.github.landwarderer.futon.core.util.ext.mapSortedByCount
import io.github.landwarderer.futon.core.util.ext.sortedWithSafe
import io.github.landwarderer.futon.core.util.ext.toList
import io.github.landwarderer.futon.core.util.ext.toLocale
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.filter.ui.model.FilterProperty
import io.github.landwarderer.futon.parsers.model.ContentType
import io.github.landwarderer.futon.parsers.model.MangaParserSource
import io.github.landwarderer.futon.parsers.util.mapToSet
import java.util.EnumSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
	private val repository: MangaSourcesRepository,
	@LocalizedAppContext context: Context,
) : BaseViewModel() {

	private val allSources = repository.allMangaSources
	private val localesGroups by lazy { allSources.groupBy { it.locale.toLocale() } }

	private var updateJob: Job

	val locales = MutableStateFlow(
		FilterProperty<Locale>(
			availableItems = listOf(Locale.ROOT),
			selectedItems = setOf(Locale.ROOT),
			isLoading = true,
			error = null,
		),
	)

	val types = MutableStateFlow(
		FilterProperty(
			availableItems = listOf(ContentType.MANGA),
			selectedItems = setOf(ContentType.MANGA),
			isLoading = true,
			error = null,
		),
	)

	init {
		updateJob = launchJob(Dispatchers.IO) {
			val contentTypes = allSources.mapSortedByCount { it.contentType }
			types.value = types.value.copy(
				availableItems = contentTypes,
				isLoading = false,
			)
			val languages = localesGroups.keys.associateBy { x -> x.language }
			val selectedLocales = HashSet<Locale>(2)
			ConfigurationCompat.getLocales(context.resources.configuration).toList()
				.firstNotNullOfOrNull { lc -> languages[lc.language] }
				?.let { selectedLocales += it }
			selectedLocales += Locale.ROOT
			locales.value = locales.value.copy(
				availableItems = localesGroups.keys.sortedWithSafe(LocaleComparator()),
				selectedItems = selectedLocales,
				isLoading = false,
			)
			repository.clearNewSourcesBadge()
			commit()
		}
	}

	fun setLocaleChecked(locale: Locale, isChecked: Boolean) {
		val snapshot = locales.value
		locales.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + locale
			} else {
				snapshot.selectedItems - locale
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob.join()
			commit()
		}
	}

	fun setTypeChecked(type: ContentType, isChecked: Boolean) {
		val snapshot = types.value
		types.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + type
			} else {
				snapshot.selectedItems - type
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob.join()
			commit()
		}
	}

	private suspend fun commit() {
		val languages = locales.value.selectedItems.mapToSet { it.language }
		val types = types.value.selectedItems
		val enabledSources = allSources.filterTo(EnumSet.noneOf(MangaParserSource::class.java)) { x ->
			x.contentType in types && x.locale in languages
		}
		repository.setSourcesEnabledExclusive(enabledSources)
	}
}
