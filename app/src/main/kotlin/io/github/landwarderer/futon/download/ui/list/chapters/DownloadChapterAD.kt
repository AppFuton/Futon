package io.github.landwarderer.futon.download.ui.list.chapters

import androidx.core.content.ContextCompat
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import androidx.core.view.isVisible
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.drawableEnd
import io.github.landwarderer.futon.databinding.ItemChapterDownloadBinding

fun downloadChapterAD(
	onDeleteClick: (DownloadChapter) -> Unit,
) = adapterDelegateViewBinding<DownloadChapter, DownloadChapter, ItemChapterDownloadBinding>(
	{ layoutInflater, parent -> ItemChapterDownloadBinding.inflate(layoutInflater, parent, false) },
) {

	val iconDone = ContextCompat.getDrawable(context, R.drawable.ic_check)

	binding.buttonDelete.setOnClickListener {
		onDeleteClick(item)
	}

	bind {
		binding.textViewNumber.text = item.number
		binding.textViewTitle.text = item.name
		binding.textViewTitle.drawableEnd = if (item.isDownloaded) iconDone else null
		binding.buttonDelete.isVisible = item.isDownloaded
	}
}
