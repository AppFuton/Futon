package io.github.landwarderer.futon.tracker.domain.model

import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.util.ext.findCloudFlareException
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.ifZero

sealed interface MangaUpdates {

	val manga: Manga

	data class Success(
		override val manga: Manga,
		val branch: String?,
		val newChapters: List<MangaChapter>,
		val isValid: Boolean,
	) : MangaUpdates {

		fun isNotEmpty() = newChapters.isNotEmpty()

		fun lastChapterDate(): Long {
			val lastChapter = newChapters.lastOrNull()
			return lastChapter?.uploadDate?.ifZero { System.currentTimeMillis() }
				?: (manga.getChapters(branch).lastOrNull()?.uploadDate ?: 0L)
		}
	}

	data class Failure(
		override val manga: Manga,
		val error: Throwable?,
	) : MangaUpdates {

		fun shouldRetry() = error is TooManyRequestExceptions

		/** The source wants a captcha solved; solving it unblocks the source. */
		fun isCaptchaRequired() = error?.findCloudFlareException() is CloudFlareProtectedException

		/**
		 * The source rejects every request for now (Cloudflare or rate limiting), so the rest of its manga
		 * would fail the same way.
		 */
		fun isSourceBlocked() = shouldRetry() || error?.findCloudFlareException() != null
	}
}
