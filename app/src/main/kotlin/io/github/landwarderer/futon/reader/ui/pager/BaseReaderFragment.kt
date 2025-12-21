package io.github.landwarderer.futon.reader.ui.pager

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import io.github.landwarderer.futon.core.prefs.ReaderAnimation
import io.github.landwarderer.futon.core.ui.BaseFragment
import io.github.landwarderer.futon.core.ui.widgets.ZoomControl
import io.github.landwarderer.futon.core.util.ext.isAnimationsEnabled
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.ReaderViewModel

abstract class BaseReaderFragment<B : ViewBinding> : BaseFragment<B>(), ZoomControl.ZoomControlListener {

	protected val viewModel by activityViewModels<ReaderViewModel>()

	protected var readerAdapter: BaseReaderAdapter<*>? = null
		private set

	override fun onViewBindingCreated(binding: B, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		readerAdapter = onCreateAdapter()

		viewModel.content.observe(viewLifecycleOwner) {
			// Determine which state to use for restoring position:
			// - content.state: explicitly set state (e.g., after mode switch or chapter change)
			// - getCurrentState(): current reading position saved in SavedStateHandle
			val currentState = viewModel.getCurrentState()
			val pendingState = when {
				// If content.state is null and we have pages, use getCurrentState
				it.state == null
					&& it.pages.isNotEmpty()
					&& readerAdapter?.hasItems != true -> currentState

				// use currentState only if it matches the current pages (to avoid the error message)
				readerAdapter?.hasItems != true
					&& it.state != currentState
					&& currentState != null
					&& it.pages.any { page -> page.chapterId == currentState.chapterId } -> currentState

				// Otherwise, use content.state (normal flow, mode switch, chapter change)
				else -> it.state
			}
			onPagesChanged(it.pages, pendingState)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onPause() {
		super.onPause()
		viewModel.saveCurrentState(getCurrentState())
	}

	override fun onDestroyView() {
		viewModel.saveCurrentState(getCurrentState())
		readerAdapter = null
		super.onDestroyView()
	}

	protected fun requireAdapter() = checkNotNull(readerAdapter) {
		"Adapter was not created or already destroyed"
	}

	protected fun isAnimationEnabled(): Boolean {
		return context?.isAnimationsEnabled == true && viewModel.pageAnimation.value != ReaderAnimation.NONE
	}

	abstract fun switchPageBy(delta: Int)

	abstract fun switchPageTo(position: Int, smooth: Boolean)

	open fun scrollBy(delta: Int, smooth: Boolean): Boolean = false

	abstract fun getCurrentState(): ReaderState?

	protected abstract fun onCreateAdapter(): BaseReaderAdapter<*>

	protected abstract suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?)
}
