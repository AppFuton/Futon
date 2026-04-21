package io.github.landwarderer.futon.core.prefs

import androidx.annotation.Keep

@Keep
enum class GitHubMirror {
	NATIVE, KKGITHUB, GHPROXY, GHPROXY_NET;
}
