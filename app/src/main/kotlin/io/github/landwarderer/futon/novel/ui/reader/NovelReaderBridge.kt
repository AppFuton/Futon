package io.github.landwarderer.futon.novel.ui.reader

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NovelReaderBridge(
	private val scope: CoroutineScope,
	private val onScroll: (percent: Float) -> Unit,
	private val onNearBottomReached: () -> Unit,
	private val onCenterTapped: () -> Unit,
	private val onPageChange: (currentPage: Int, totalPages: Int) -> Unit,
) {

	@JavascriptInterface
	fun onScrollProgress(percent: Float) {
		scope.launch(Dispatchers.Main) {
			onScroll(percent)
		}
	}

	@JavascriptInterface
	fun onNearBottom() {
		scope.launch(Dispatchers.Main) {
			onNearBottomReached()
		}
	}

	@JavascriptInterface
	fun onCenterTap() {
		scope.launch(Dispatchers.Main) {
			onCenterTapped()
		}
	}

	@JavascriptInterface
	fun onPageChanged(currentPage: Int, totalPages: Int) {
		scope.launch(Dispatchers.Main) {
			onPageChange(currentPage, totalPages)
		}
	}

	@JavascriptInterface
	fun log(msg: String) {
		android.util.Log.d("NovelReaderBridge", msg)
	}
}
