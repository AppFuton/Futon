package io.github.landwarderer.futon.alternatives.ui

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ItemEmptyStateBinding
import io.github.landwarderer.futon.list.ui.model.ListModel

object SourceReplacementEmptyState : ListModel {
	override fun equals(other: Any?): Boolean = other === this

	override fun hashCode(): Int = javaClass.hashCode()

	override fun areItemsTheSame(other: ListModel): Boolean = other === this
}

fun sourceReplacementEmptyStateAD(
	onEditTitle: () -> Unit,
	onSkip: () -> Unit,
) = adapterDelegateViewBinding<SourceReplacementEmptyState, ListModel, ItemEmptyStateBinding>(
	{ inflater, parent -> ItemEmptyStateBinding.inflate(inflater, parent, false) },
) {
	val skipButton = MaterialButton(
		context,
		null,
		com.google.android.material.R.attr.materialButtonOutlinedStyle,
	).apply {
		setText(R.string.skip)
		layoutParams = LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
		).apply {
			topMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
		}
		setOnClickListener { onSkip() }
	}
	binding.root.addView(skipButton)
	binding.buttonRetry.apply {
		isVisible = true
		setText(R.string.edit)
		setOnClickListener { onEditTitle() }
	}

	bind {
		binding.icon.isVisible = true
		binding.icon.setImageAsync(R.drawable.ic_empty_common)
		binding.textPrimary.setText(R.string.nothing_found)
		binding.textSecondary.apply {
			isVisible = true
			setText(R.string.source_replacement_empty_summary)
		}
	}
}