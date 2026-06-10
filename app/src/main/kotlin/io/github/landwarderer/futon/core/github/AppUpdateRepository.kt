package io.github.landwarderer.futon.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext context: Context,
) {
	private val changelogUrl = buildString {
		append("https://raw.githubusercontent.com/")
		append(context.getString(R.string.github_updates_repo))
		append("/refs/heads/devel/CHANGELOG.md")
	}

	suspend fun fetchChangelog(): String? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(changelogUrl)
				.build()
			okHttp.newCall(request).await().body?.string()
		}.onFailure {
			it.printStackTraceDebug("AppUpdateRepository::fetchChangelog")
		}.getOrNull()
	}
}
