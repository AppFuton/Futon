package io.github.landwarderer.futon.history.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.calculateTimeAgo
import io.github.landwarderer.futon.core.util.ext.formatDurationShort
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.databinding.ItemHistoryBentoBinding
import io.github.landwarderer.futon.databinding.ItemHistoryHeaderBinding
import io.github.landwarderer.futon.databinding.ItemHistoryTileBinding
import io.github.landwarderer.futon.history.domain.activityScore
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import org.koitharu.kotatsu.parsers.model.Manga
import java.time.Instant
import java.util.concurrent.TimeUnit
import com.google.android.material.R as materialR

private const val TOTAL_SPANS = 12
private const val MEDIUM_SPANS = 4
private const val SMALL_SPANS = 3
private const val MEDIUM_COUNT = 6
private const val TILE_MARGIN_DP = 4
private const val EARLIER_ALPHA = 0.62f

private const val TYPE_BENTO = 0
private const val TYPE_MEDIUM = 1
private const val TYPE_HEADER = 2
private const val TYPE_SMALL = 3

class HistoryRecentView(
	context: Context,
	private val onOpen: (Manga) -> Unit,
) : RecyclerView(context), HistoryModeView {

	private sealed interface Row {
		data class Bento(val hero: HistoryEntry, val first: HistoryEntry?, val second: HistoryEntry?) : Row
		data class Medium(val entry: HistoryEntry) : Row
		data object Header : Row
		data class Small(val entry: HistoryEntry) : Row
	}

	private val rows = ArrayList<Row>()
	private val captionColor = context.getThemeColor(materialR.attr.colorSurface, 0.94f)
	private var isStatsEnabled = false

	init {
		val horizontal = dp(12)
		setPadding(horizontal, dp(4), horizontal, 0)
		clipToPadding = false
		itemAnimator = null
		layoutManager = GridLayoutManager(context, TOTAL_SPANS).apply {
			spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
				override fun getSpanSize(position: Int) = when (rows[position]) {
					is Row.Medium -> MEDIUM_SPANS
					is Row.Small -> SMALL_SPANS
					else -> TOTAL_SPANS
				}
			}
		}
		adapter = RecentAdapter()
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun setEntries(entries: List<HistoryEntry>, isStatsEnabled: Boolean) {
		this.isStatsEnabled = isStatsEnabled
		val now = System.currentTimeMillis()
		val ranked = entries.sortedByDescending { activityScore(it.updatedAt, it.sessions, now) }
		rows.clear()
		ranked.firstOrNull()?.let { rows += Row.Bento(it, ranked.getOrNull(1), ranked.getOrNull(2)) }
		ranked.drop(3).take(MEDIUM_COUNT).mapTo(rows) { Row.Medium(it) }
		val earlier = ranked.drop(3 + MEDIUM_COUNT)
		if (earlier.isNotEmpty()) {
			rows += Row.Header
			earlier.mapTo(rows) { Row.Small(it) }
		}
		adapter?.notifyDataSetChanged()
	}

	override val scrollsVertically = true

	override fun setBottomInset(inset: Int) {
		updatePadding(bottom = inset)
	}

	private fun coverHeight(spans: Int): Int {
		val cell = (measuredWidth - paddingLeft - paddingRight) * spans / TOTAL_SPANS - dp(TILE_MARGIN_DP) * 2
		return cell * 18 / 13
	}

	private fun ItemHistoryTileBinding.bind(entry: HistoryEntry, isHero: Boolean, hasCaption: Boolean) {
		root.setOnClickListener { onOpen(entry.manga) }
		imageViewCover.setImageAsync(entry.manga.coverUrl, entry.manga)
		progressView.setProgress(entry.percent, false)
		layoutCaption.isVisible = hasCaption
		root.contentDescription = entry.manga.title
		if (!hasCaption) {
			return
		}
		layoutCaption.setBackgroundColor(captionColor)
		textViewTitle.text = entry.manga.title
		textViewTitle.textSize = if (isHero) 20f else 12f
		textViewMeta.text = entry.metaText()
		sparkBars.isVisible = isHero && isStatsEnabled
		if (sparkBars.isVisible) {
			sparkBars.setValues(entry.lastSevenDays())
		}
	}

	private fun HistoryEntry.metaText(): String {
		val week = readingTimeSince(lastWeekStart())
		if (week > 0L) {
			resources.formatDurationShort(week)?.let { return context.getString(R.string.history_this_week, it) }
		}
		return calculateTimeAgo(Instant.ofEpochMilli(updatedAt))?.format(context).orEmpty()
	}

	private fun HistoryEntry.lastSevenDays(): LongArray {
		val now = System.currentTimeMillis()
		val day = TimeUnit.DAYS.toMillis(1)
		return LongArray(7) { i ->
			val age = 6 - i
			sessions.sumOf { if ((now - it.startedAt) / day == age.toLong()) it.duration else 0L }
		}
	}

	private class Holder(val binding: ViewBinding) : ViewHolder(binding.root)

	private inner class RecentAdapter : Adapter<Holder>() {

		override fun getItemCount() = rows.size

		override fun getItemViewType(position: Int) = when (rows[position]) {
			is Row.Bento -> TYPE_BENTO
			is Row.Medium -> TYPE_MEDIUM
			is Row.Header -> TYPE_HEADER
			is Row.Small -> TYPE_SMALL
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val inflater = LayoutInflater.from(parent.context)
			return Holder(
				when (viewType) {
					TYPE_BENTO -> ItemHistoryBentoBinding.inflate(inflater, parent, false)
					TYPE_HEADER -> ItemHistoryHeaderBinding.inflate(inflater, parent, false)
					else -> ItemHistoryTileBinding.inflate(inflater, parent, false)
				},
			)
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			when (val row = rows[position]) {
				is Row.Bento -> with(holder.binding as ItemHistoryBentoBinding) {
					root.updateLayoutParams { height = coverHeight(TOTAL_SPANS * 2 / 3) + dp(TILE_MARGIN_DP) * 2 }
					tileHero.bind(row.hero, isHero = true, hasCaption = true)
					tileFirst.root.isVisible = row.first != null
					tileSecond.root.isVisible = row.second != null
					row.first?.let { tileFirst.bind(it, isHero = false, hasCaption = true) }
					row.second?.let { tileSecond.bind(it, isHero = false, hasCaption = true) }
				}

				is Row.Medium -> with(holder.binding as ItemHistoryTileBinding) {
					root.alpha = 1f
					root.updateLayoutParams { height = coverHeight(MEDIUM_SPANS) }
					bind(row.entry, isHero = false, hasCaption = true)
				}

				is Row.Small -> with(holder.binding as ItemHistoryTileBinding) {
					root.alpha = EARLIER_ALPHA
					root.updateLayoutParams { height = coverHeight(SMALL_SPANS) }
					bind(row.entry, isHero = false, hasCaption = false)
				}

				is Row.Header -> Unit
			}
		}
	}
}
