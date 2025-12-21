package io.github.landwarderer.futon.list.ui.adapter

import io.github.landwarderer.futon.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
