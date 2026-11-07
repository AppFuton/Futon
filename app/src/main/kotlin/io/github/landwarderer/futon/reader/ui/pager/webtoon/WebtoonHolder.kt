package io.github.landwarderer.futon.reader.ui.pager.webtoon

import android.view.View
import androidx.lifecycle.LifecycleOwner
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.os.NetworkState
import io.github.landwarderer.futon.databinding.ItemPageWebtoonBinding
import io.github.landwarderer.futon.reader.domain.PageLoader
import io.github.landwarderer.futon.reader.ui.config.ReaderSettings
import io.github.landwarderer.futon.reader.ui.pager.BasePageHolder

class WebtoonHolder(
	owner: LifecycleOwner,
	binding: ItemPageWebtoonBinding,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : BasePageHolder<ItemPageWebtoonBinding>(
	binding = binding,
	loader = loader,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
	lifecycleOwner = owner,
) {

	override val ssiv = binding.ssiv

	private var scrollToRestore = 0

	init {
		bindingInfo.progressBar.setVisibilityAfterHide(View.GONE)
	}

	override fun onReady() {
		binding.ssiv.colorFilter = settings.colorFilter?.toColorFilter()
		val restore = scrollToRestore
		scrollToRestore = 0
		val recyclerView = itemView.parent as? WebtoonRecyclerView
		when {
			restore != 0 -> binding.ssiv.scrollTo(restore)
			// Also reached on every settings change, so this must leave an already placed page alone
			recyclerView != null -> recyclerView.onPageReady(itemView as WebtoonFrameLayout)
			else -> binding.ssiv.scrollTo(binding.ssiv.getScroll())
		}
	}

	fun getScrollY() = binding.ssiv.getScroll()

	fun restoreScroll(scroll: Int) {
		if (binding.ssiv.isReady) {
			binding.ssiv.scrollTo(scroll)
		} else {
			scrollToRestore = scroll
		}
	}
}
