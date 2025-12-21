package io.github.landwarderer.futon.scrobbling.common.ui.config.adapter

import androidx.core.view.isInvisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ItemHeaderBinding
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.scrobbling.common.domain.model.ScrobblingStatus

fun scrobblingHeaderAD() = adapterDelegateViewBinding<ScrobblingStatus, ListModel, ItemHeaderBinding>(
	{ inflater, parent -> ItemHeaderBinding.inflate(inflater, parent, false) },
) {

	binding.buttonMore.isInvisible = true
	val strings = context.resources.getStringArray(R.array.scrobbling_statuses)

	bind {
		binding.textViewTitle.text = strings.getOrNull(item.ordinal)
	}
}
