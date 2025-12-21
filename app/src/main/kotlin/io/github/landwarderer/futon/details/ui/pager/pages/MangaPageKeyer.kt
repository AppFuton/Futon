package io.github.landwarderer.futon.details.ui.pager.pages

import coil3.key.Keyer
import coil3.request.Options
import io.github.landwarderer.futon.parsers.model.MangaPage

class MangaPageKeyer : Keyer<MangaPage> {

	override fun key(data: MangaPage, options: Options) = data.url
}
