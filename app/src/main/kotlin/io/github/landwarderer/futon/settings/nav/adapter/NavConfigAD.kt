package io.github.landwarderer.futon.settings.nav.adapter

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.list.OnListItemClickListener
import io.github.landwarderer.futon.core.util.ext.setTextAndVisible
import io.github.landwarderer.futon.databinding.ItemNavAvailableBinding
import io.github.landwarderer.futon.databinding.ItemNavConfigBinding
import io.github.landwarderer.futon.list.ui.model.ListModel
import io.github.landwarderer.futon.settings.nav.model.NavItemAddModel
import io.github.landwarderer.futon.settings.nav.model.NavItemConfigModel

@SuppressLint("ClickableViewAccessibility")
fun navConfigAD(
	clickListener: OnListItemClickListener<NavItem>,
) = adapterDelegateViewBinding<NavItemConfigModel, ListModel, ItemNavConfigBinding>(
	{ layoutInflater, parent -> ItemNavConfigBinding.inflate(layoutInflater, parent, false) },
) {

	val eventListener = object : View.OnClickListener, View.OnTouchListener {
		override fun onClick(v: View) = clickListener.onItemClick(item.item, v)

		override fun onTouch(v: View?, event: MotionEvent): Boolean =
			event.actionMasked == MotionEvent.ACTION_DOWN &&
				clickListener.onItemLongClick(item.item, itemView)
	}
	binding.imageViewRemove.setOnClickListener(eventListener)
	binding.imageViewReorder.setOnTouchListener(eventListener)

	bind {
		with(binding.textViewTitle) {
			isEnabled = item.disabledHintResId == 0
			setText(item.item.title)
			setCompoundDrawablesRelativeWithIntrinsicBounds(item.item.icon, 0, 0, 0)
		}
		binding.textViewHint.setTextAndVisible(item.disabledHintResId)
	}
}

fun navAvailableAD(
	clickListener: OnListItemClickListener<NavItem>,
) = adapterDelegateViewBinding<NavItem, NavItem, ItemNavAvailableBinding>(
	{ layoutInflater, parent -> ItemNavAvailableBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		clickListener.onItemClick(item, v)
	}

	bind {
		with(binding.root) {
			setText(item.title)
			setCompoundDrawablesRelativeWithIntrinsicBounds(item.icon, 0, 0, 0)
		}
	}
}

fun navAddAD(
	clickListener: View.OnClickListener,
) = adapterDelegateViewBinding<NavItemAddModel, ListModel, ItemNavAvailableBinding>(
	{ layoutInflater, parent -> ItemNavAvailableBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener(clickListener)
	binding.root.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0)

	bind {
		with(binding.root) {
			setText(if (item.canAdd) R.string.add else R.string.items_limit_exceeded)
			isEnabled = item.canAdd
		}
	}
}
