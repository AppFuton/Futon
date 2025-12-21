package io.github.landwarderer.futon.core.parser.favicon

import android.net.Uri
import io.github.landwarderer.futon.parsers.model.MangaSource

const val URI_SCHEME_FAVICON = "favicon"

fun MangaSource.faviconUri(): Uri = Uri.fromParts(URI_SCHEME_FAVICON, name, null)