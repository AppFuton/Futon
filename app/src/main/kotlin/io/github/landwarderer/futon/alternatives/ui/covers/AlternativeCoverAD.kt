package io.github.landwarderer.futon.alternatives.ui.covers

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.getTitle
import io.github.landwarderer.futon.core.ui.list.AdapterDelegateClickListenerAdapter
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.futon.databinding.ItemCoverAlternativeBinding
import io.github.landwarderer.futon.list.ui.model.ListModel

fun alternativeCoverAD(
	listener: OnListItemClickListener<AlternativeCoverModel>,
) = adapterDelegateViewBinding<AlternativeCoverModel, ListModel, ItemCoverAlternativeBinding>(
	{ inflater, parent -> ItemCoverAlternativeBinding.inflate(inflater, parent, false) },
) {

	itemView.setOnClickListener(AdapterDelegateClickListenerAdapter(this, listener))

	bind {
		binding.textViewSource.text = if (item.isCurrent) {
			context.getString(R.string.current_cover)
		} else {
			item.source.getTitle(context)
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		// A ring on the thumbnail ties the carousel to the big preview above it.
		binding.imageViewCover.strokeWidth = if (item.isSelected) {
			context.resources.getDimension(R.dimen.cover_selection_stroke)
		} else {
			0f
		}
	}
}
