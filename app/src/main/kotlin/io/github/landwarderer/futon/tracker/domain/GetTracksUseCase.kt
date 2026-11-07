package io.github.landwarderer.futon.tracker.domain

import io.github.landwarderer.futon.tracker.domain.model.MangaTracking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GetTracksUseCase @Inject constructor(
	private val repository: TrackingRepository,
) {

	suspend operator fun invoke(limit: Int): List<MangaTracking> {
		repository.updateTracks()
		val now = System.currentTimeMillis()
		return repository.getTracks(
			offset = 0,
			limit = limit,
			minActivityTime = now - TimeUnit.DAYS.toMillis(MAX_INACTIVE_DAYS),
			// Inactive manga must still be re-checked occasionally: last_chapter_date only
			// advances when a check runs, so without this floor a manga that crosses the
			// inactivity window (hiatus, or a source without upload dates) would be
			// excluded from checking forever.
			staleCheckTime = now - TimeUnit.DAYS.toMillis(STALE_CHECK_DAYS),
		)
	}

	private companion object {

		const val MAX_INACTIVE_DAYS = 90L
		const val STALE_CHECK_DAYS = 7L
	}
}
