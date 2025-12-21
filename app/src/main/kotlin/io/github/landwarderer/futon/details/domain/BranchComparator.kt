package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.util.LocaleStringComparator
import io.github.landwarderer.futon.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
