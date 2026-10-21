package io.github.landwarderer.futon.novel.ui.reader.pdf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.databinding.ItemPdfPageBinding
import io.github.landwarderer.futon.novel.data.pdf.PdfRendererHelper

class PdfPageAdapter(
	private val scope: CoroutineScope,
	private val getPdfHelper: () -> PdfRendererHelper?,
	private val onPageClick: (() -> Unit)? = null,
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

	private var pageCount: Int = 0

	fun setPageCount(count: Int) {
		if (pageCount != count) {
			pageCount = count
			notifyDataSetChanged()
		}
	}

	override fun getItemCount(): Int = pageCount

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
		val binding = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return PageViewHolder(binding)
	}

	override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
		val targetWidth = holder.itemView.resources.displayMetrics.widthPixels.takeIf { it > 0 } ?: 1080
		holder.bind(position, getPdfHelper(), targetWidth)
	}

	override fun onViewRecycled(holder: PageViewHolder) {
		super.onViewRecycled(holder)
		holder.recycle()
	}

	inner class PageViewHolder(val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {
		private var loadJob: Job? = null

		init {
			binding.root.setOnClickListener {
				onPageClick?.invoke()
			}
		}

		fun bind(position: Int, helper: PdfRendererHelper?, targetWidth: Int) {
			loadJob?.cancel()
			binding.imageViewPage.setImageBitmap(null)
			val estimatedHeight = (targetWidth * 1.4f).toInt()
			binding.root.minimumHeight = estimatedHeight

			if (helper == null) {
				binding.progressBarPage.isVisible = false
				return
			}
			binding.progressBarPage.isVisible = true

			loadJob = scope.launch {
				val bitmap = withContext(Dispatchers.Default) {
					try {
						helper.renderPage(position, targetWidth)
					} catch (e: CancellationException) {
						throw e
					} catch (e: Throwable) {
						e.printStackTraceDebug()
						null
					}
				}
				if (isActive) {
					binding.progressBarPage.isVisible = false
					if (bitmap != null) {
						binding.root.minimumHeight = 0
						binding.imageViewPage.setImageBitmap(bitmap)
					}
				}
			}
		}

		fun recycle() {
			loadJob?.cancel()
			loadJob = null
			binding.imageViewPage.setImageBitmap(null)
			binding.progressBarPage.isVisible = false
		}
	}
}
