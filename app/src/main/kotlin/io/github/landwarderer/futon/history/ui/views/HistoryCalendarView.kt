package io.github.landwarderer.futon.history.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.formatDurationShort
import io.github.landwarderer.futon.core.util.ext.getThemeColor
import io.github.landwarderer.futon.databinding.ItemHistoryCalendarDayBinding
import io.github.landwarderer.futon.databinding.ItemHistoryCalendarHeaderBinding
import io.github.landwarderer.futon.databinding.ItemHistoryCalendarSummaryBinding
import io.github.landwarderer.futon.databinding.ItemHistoryCalendarWeekdayBinding
import io.github.landwarderer.futon.databinding.ItemHistorySessionBinding
import io.github.landwarderer.futon.history.domain.leadingCalendarBlanks
import io.github.landwarderer.futon.history.domain.model.HistoryEntry
import org.koitharu.kotatsu.parsers.model.Manga
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.appcompat.R as appcompatR

private const val DAYS_IN_WEEK = 7
private const val MIN_ART_ALPHA = 0.5f

private const val TYPE_HEADER = 0
private const val TYPE_WEEKDAY = 1
private const val TYPE_BLANK = 2
private const val TYPE_DAY = 3
private const val TYPE_SUMMARY = 4
private const val TYPE_SESSION = 5

class HistoryCalendarView(
	context: Context,
	private val onOpen: (Manga) -> Unit,
) : RecyclerView(context), HistoryModeView {

	private data class DayRead(
		val entry: HistoryEntry,
		val startedAt: Long,
		val duration: Long,
		val pages: Int,
	)

	private sealed interface Row {
		data object Header : Row
		data class Weekday(val day: DayOfWeek) : Row
		data object Blank : Row
		data class Day(val date: LocalDate, val reads: List<DayRead>, val total: Long) : Row
		data object Summary : Row
		data class Session(val read: DayRead) : Row
	}

	private val locale = Locale.getDefault()
	private val zone = ZoneId.systemDefault()
	private val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
	private val monthFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale)
	private val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
	private val timeFormat = DateFormat.getTimeFormat(context)
	private val selectedStroke = context.getThemeColor(appcompatR.attr.colorPrimary)
	private val rows = ArrayList<Row>()
	private var reads: Map<LocalDate, List<DayRead>> = emptyMap()
	private var firstMonth = YearMonth.now()
	private var month = YearMonth.now()
	private var selected = LocalDate.now()
	private var isStatsEnabled = false

	init {
		val horizontal = dp(16)
		setPadding(horizontal, 0, horizontal, 0)
		clipToPadding = false
		itemAnimator = null
		layoutManager = GridLayoutManager(context, DAYS_IN_WEEK).apply {
			spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
				override fun getSpanSize(position: Int) = when (rows[position]) {
					is Row.Weekday, is Row.Blank, is Row.Day -> 1
					else -> DAYS_IN_WEEK
				}
			}
		}
		adapter = CalendarAdapter()
	}

	override fun setEntries(entries: List<HistoryEntry>, isStatsEnabled: Boolean) {
		this.isStatsEnabled = isStatsEnabled
		reads = buildReads(entries, isStatsEnabled)
		firstMonth = reads.keys.minOrNull()?.let(YearMonth::from) ?: YearMonth.now()
		rebuild()
	}

	override val scrollsVertically = true

	override fun setBottomInset(inset: Int) {
		updatePadding(bottom = inset)
	}

	private fun buildReads(entries: List<HistoryEntry>, useSessions: Boolean): Map<LocalDate, List<DayRead>> {
		val result = HashMap<LocalDate, MutableList<DayRead>>()
		for (entry in entries) {
			if (useSessions && entry.sessions.isNotEmpty()) {
				entry.sessions.groupBy { it.startedAt.toLocalDate() }.forEach { (date, sessions) ->
					result.getOrPut(date) { ArrayList() } += DayRead(
						entry = entry,
						startedAt = sessions.minOf { it.startedAt },
						duration = sessions.sumOf { it.duration },
						pages = sessions.sumOf { it.pages },
					)
				}
			} else if (!useSessions) {
				result.getOrPut(entry.updatedAt.toLocalDate()) { ArrayList() } += DayRead(entry, entry.updatedAt, 0L, 0)
			}
		}
		result.values.forEach { list -> list.sortWith(compareByDescending<DayRead> { it.duration }.thenByDescending { it.startedAt }) }
		return result
	}

	private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

	@SuppressLint("NotifyDataSetChanged")
	private fun rebuild() {
		rows.clear()
		rows += Row.Header
		var weekday = firstDayOfWeek
		repeat(DAYS_IN_WEEK) {
			rows += Row.Weekday(weekday)
			weekday = weekday.plus(1)
		}
		val first = month.atDay(1)
		repeat(leadingCalendarBlanks(first, firstDayOfWeek)) { rows += Row.Blank }
		val today = LocalDate.now()
		var last = month.atEndOfMonth()
		if (month == YearMonth.from(today)) {
			val daysLeftInWeek = (firstDayOfWeek.plus(6L).value - today.dayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
			last = minOf(last, today.plusDays(daysLeftInWeek.toLong()))
		}
		var date = first
		while (!date.isAfter(last)) {
			val dayReads = reads[date].orEmpty()
			rows += Row.Day(date, dayReads, dayReads.sumOf { it.duration })
			date = date.plusDays(1)
		}
		rows += Row.Summary
		reads[selected].orEmpty().mapTo(rows) { Row.Session(it) }
		adapter?.notifyDataSetChanged()
	}

	private fun showMonth(target: YearMonth) {
		month = target
		val today = LocalDate.now()
		selected = if (target == YearMonth.from(today)) {
			today
		} else {
			reads.keys.filter { YearMonth.from(it) == target }.maxOrNull() ?: target.atDay(1)
		}
		rebuild()
	}

	private class Holder(val binding: ViewBinding) : ViewHolder(binding.root)

	private inner class CalendarAdapter : Adapter<Holder>() {

		override fun getItemCount() = rows.size

		override fun getItemViewType(position: Int) = when (rows[position]) {
			is Row.Header -> TYPE_HEADER
			is Row.Weekday -> TYPE_WEEKDAY
			is Row.Blank -> TYPE_BLANK
			is Row.Day -> TYPE_DAY
			is Row.Summary -> TYPE_SUMMARY
			is Row.Session -> TYPE_SESSION
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val inflater = LayoutInflater.from(parent.context)
			return Holder(
				when (viewType) {
					TYPE_HEADER -> ItemHistoryCalendarHeaderBinding.inflate(inflater, parent, false)
					TYPE_WEEKDAY -> ItemHistoryCalendarWeekdayBinding.inflate(inflater, parent, false)
					TYPE_SUMMARY -> ItemHistoryCalendarSummaryBinding.inflate(inflater, parent, false)
					TYPE_SESSION -> ItemHistorySessionBinding.inflate(inflater, parent, false)
					else -> ItemHistoryCalendarDayBinding.inflate(inflater, parent, false)
				},
			)
		}

		override fun onBindViewHolder(holder: Holder, position: Int) {
			when (val row = rows[position]) {
				is Row.Header -> bindHeader(holder.binding as ItemHistoryCalendarHeaderBinding)
				is Row.Weekday -> (holder.binding as ItemHistoryCalendarWeekdayBinding).root.text =
					row.day.getDisplayName(TextStyle.NARROW, locale)

				is Row.Blank -> (holder.binding as ItemHistoryCalendarDayBinding).root.isVisible = false
				is Row.Day -> bindDay(holder.binding as ItemHistoryCalendarDayBinding, row)
				is Row.Summary -> bindSummary(holder.binding as ItemHistoryCalendarSummaryBinding)
				is Row.Session -> bindSession(holder.binding as ItemHistorySessionBinding, row.read)
			}
		}

		private fun bindHeader(binding: ItemHistoryCalendarHeaderBinding) = with(binding) {
			val current = YearMonth.now()
			textViewMonth.text = month.format(monthFormat)
			val total = reads.entries.sumOf { (date, list) ->
				if (YearMonth.from(date) == month) list.sumOf { it.duration } else 0L
			}
			textViewTotal.isVisible = isStatsEnabled && total > 0L
			textViewTotal.text = resources.formatDurationShort(total)
			buttonPrevious.isEnabled = month > firstMonth
			buttonNext.isEnabled = month < current
			buttonPrevious.alpha = if (buttonPrevious.isEnabled) 1f else 0.3f
			buttonNext.alpha = if (buttonNext.isEnabled) 1f else 0.3f
			buttonPrevious.setOnClickListener { showMonth(month.minusMonths(1)) }
			buttonNext.setOnClickListener { showMonth(month.plusMonths(1)) }
		}

		private fun bindDay(binding: ItemHistoryCalendarDayBinding, row: Row.Day) = with(binding) {
			root.isVisible = true
			val today = LocalDate.now()
			val isFuture = row.date.isAfter(today)
			val top = row.reads.firstOrNull()
			root.alpha = if (isFuture) 0.35f else 1f
			root.isEnabled = !isFuture
			textViewDay.text = row.date.dayOfMonth.toString()
			textViewDay.isActivated = row.date == today
			root.strokeColor = selectedStroke
			root.strokeWidth = if (row.date == selected) dp(2) else 0
			root.contentDescription = row.date.format(dayFormat)
			val peak = max(1L, currentPeak())
			if (top != null) {
				imageViewCover.isVisible = true
				imageViewCover.setImageAsync(top.entry.manga.coverUrl, top.entry.manga)
				imageViewCover.alpha = if (isStatsEnabled) {
					MIN_ART_ALPHA + (1f - MIN_ART_ALPHA) * row.total.toFloat() / peak
				} else {
					1f
				}
			} else {
				imageViewCover.disposeImage()
				imageViewCover.isVisible = false
			}
			progressBar.isVisible = top != null && isStatsEnabled
			progressBar.progress = (row.total * 100f / peak).roundToInt().coerceAtLeast(12)
			root.setOnClickListener {
				selected = row.date
				rebuild()
			}
		}

		private fun currentPeak(): Long = reads.entries.maxOfOrNull { (date, list) ->
			if (YearMonth.from(date) == month) list.sumOf { it.duration } else 0L
		} ?: 0L

		private fun bindSummary(binding: ItemHistoryCalendarSummaryBinding) = with(binding) {
			textViewDate.text = selected.format(dayFormat)
			val dayReads = reads[selected].orEmpty()
			textViewSummary.text = when {
				dayReads.isEmpty() -> context.getString(R.string.history_nothing_read)
				isStatsEnabled -> listOfNotNull(
					resources.formatDurationShort(dayReads.sumOf { it.duration }),
					resources.getQuantityString(R.plurals.pages, dayReads.sumOf { it.pages }, dayReads.sumOf { it.pages }),
				).joinToString(" · ")

				else -> context.getString(R.string.history_last_read_on_day)
			}
			textViewHint.isVisible = !isStatsEnabled
		}

		private fun bindSession(binding: ItemHistorySessionBinding, read: DayRead) = with(binding) {
			val manga = read.entry.manga
			root.setOnClickListener { onOpen(manga) }
			imageViewCover.setImageAsync(manga.coverUrl, manga)
			textViewTitle.text = manga.title
			val time = timeFormat.format(Date(read.startedAt))
			textViewMeta.text = if (isStatsEnabled) {
				listOfNotNull(
					time,
					resources.formatDurationShort(read.duration),
					resources.getQuantityString(R.plurals.pages, read.pages, read.pages),
				).joinToString(" · ")
			} else {
				time
			}
		}
	}
}
