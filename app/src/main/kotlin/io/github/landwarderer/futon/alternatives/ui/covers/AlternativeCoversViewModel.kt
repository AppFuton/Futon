package io.github.landwarderer.futon.alternatives.ui.covers

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.alternatives.domain.AlternativesUseCase
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.mangaSourceExtra
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.core.util.ext.toBitmapOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.Collections
import javax.inject.Inject

private const val HASH_PARALLELISM = 4

@HiltViewModel
class AlternativeCoversViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val alternativesUseCase: AlternativesUseCase,
	@ApplicationContext private val context: Context,
	private val coil: ImageLoader,
) : BaseViewModel() {

	val manga: Manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	/** Appended to as sources answer, so covers show up without waiting for the slowest source. */
	val covers = MutableStateFlow<List<AlternativeCoverModel>>(emptyList())

	/** Which cover the big preview is showing. */
	val selectedUrl = MutableStateFlow<String?>(null)

	val isRunning = MutableStateFlow(true)

	/** Candidates are processed in parallel, so this must tolerate concurrent access. */
	private val seenKeys = Collections.synchronizedSet(HashSet<String>())

	/** Perceptual hashes of the covers already accepted; candidates are hashed in parallel. */
	private val acceptedHashes = ArrayList<Long>()
	private val hashMutex = Mutex()
	private val hashSemaphore = Semaphore(HASH_PARALLELISM)
	private var searchJob: Job? = null

	init {
		// The current cover leads the carousel so a candidate can be compared against it, and its key is
		// seeded so no source can offer the same image back as an "alternative".
		manga.coverUrl?.takeUnless { it.isEmpty() }?.let { current ->
			seenKeys += current.dedupKey()
			covers.value = listOf(
				AlternativeCoverModel(
					coverUrl = current,
					source = manga.source,
					manga = manga,
					isCurrent = true,
					isSelected = true,
				),
			)
			selectedUrl.value = current
		}
		searchJob = launchLoadingJob(Dispatchers.Default) {
			try {
				// Seed the current cover's hash first, so a source offering the same artwork back is
				// dropped rather than shown as an alternative to itself.
				manga.coverUrl?.takeUnless { it.isEmpty() }?.let { rememberHash(it, manga.source) }
				alternativesUseCase(manga, throughDisabledSources = false).collect { candidate ->
					launch { onCandidate(candidate) }
				}
			} finally {
				isRunning.value = false
			}
		}
	}

	/** Nothing more is worth fetching once a cover is chosen, or once the screen is left. */
	fun cancelSearch() {
		searchJob?.cancel()
		searchJob = null
		isRunning.value = false
	}

	override fun onCleared() {
		cancelSearch()
		super.onCleared()
	}

	fun select(url: String) {
		if (selectedUrl.value == url) {
			return
		}
		selectedUrl.value = url
		covers.update { list -> list.map { it.copy(isSelected = it.coverUrl == url) } }
	}

	private suspend fun onCandidate(candidate: Manga) {
		val url = candidate.coverUrl
		if (url.isNullOrEmpty() || !seenKeys.add(url.dedupKey())) {
			return
		}
		// Cheap url check first, then the image itself: sources republish the same artwork under
		// different names often enough that the url tells you almost nothing.
		if (!rememberHash(url, candidate.source)) {
			return
		}
		covers.update { current ->
			current + AlternativeCoverModel(
				coverUrl = url,
				source = candidate.source,
				manga = candidate,
				isCurrent = false,
				isSelected = false,
			)
		}
	}

	/**
	 * Hashes a cover and records it, returning `false` when it looks like one already accepted.
	 *
	 * A cover that cannot be decoded is kept: a duplicate is a smaller problem than a missing option.
	 */
	private suspend fun rememberHash(url: String, source: MangaSource): Boolean {
		val hash = hashSemaphore.withPermit { hashOf(url, source) } ?: return true
		return hashMutex.withLock {
			if (acceptedHashes.any { CoverHash.isSame(it, hash) }) {
				false
			} else {
				acceptedHashes.add(hash)
				true
			}
		}
	}

	private suspend fun hashOf(url: String, source: MangaSource): Long? = runCatchingCancellable {
		val request = ImageRequest.Builder(context)
			.data(url)
			.size(CoverHash.SAMPLE_WIDTH, CoverHash.SAMPLE_HEIGHT)
			.allowRgb565(false)
			.mangaSourceExtra(source)
			.build()
		coil.execute(request).toBitmapOrNull()?.let(CoverHash::of)
	}.getOrNull()

	/**
	 * Sources routinely mirror the same artwork under different urls, so comparing whole urls dedupes
	 * almost nothing. The filename is a decent proxy for "same image" without decoding anything.
	 */
	private fun String.dedupKey(): String = substringBefore('?')
		.substringAfterLast('/')
		.lowercase()
		.ifEmpty { this }
}
