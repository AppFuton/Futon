package eu.kanade.tachiyomi.source.model

import java.io.Serializable

data class Page(
	val index: Int,
	val url: String = "",
	var imageUrl: String? = null,
) : Serializable
