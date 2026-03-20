package io.github.landwarderer.futon.list.ui.model

import androidx.annotation.StringRes
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.exceptions.resolve.ExceptionResolver
import io.github.landwarderer.futon.core.util.ext.getDisplayIcon
import org.koitharu.kotatsu.parsers.util.ifZero

fun Throwable.toErrorState(canRetry: Boolean = true, @StringRes secondaryAction: Int = 0) = ErrorState(
	exception = this,
	icon = getDisplayIcon(),
	canRetry = canRetry,
	buttonText = ExceptionResolver.getResolveStringId(this).ifZero { R.string.try_again },
	secondaryButtonText = secondaryAction,
)

fun Throwable.toErrorFooter() = ErrorFooter(
	exception = this,
)

operator fun ListModel.plus(list: List<ListModel>): List<ListModel> {
	val result = ArrayList<ListModel>(list.size + 1)
	result.add(this)
	result.addAll(list)
	return result
}

operator fun ListModel.plus(other: ListModel): List<ListModel> = listOf(this, other)
