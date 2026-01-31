package eu.kanade.tachiyomi.source.model

import java.io.Serializable

class SChapterImpl : SChapter, Serializable {
	override lateinit var url: String
	override lateinit var name: String
	override var date_upload: Long = 0
	override var chapter_number: Float = -1f
	override var scanlator: String? = null
}
