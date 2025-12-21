package io.github.landwarderer.futon.reader.ui

import io.github.landwarderer.futon.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)