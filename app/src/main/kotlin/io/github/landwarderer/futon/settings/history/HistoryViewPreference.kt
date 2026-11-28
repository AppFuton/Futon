package io.github.landwarderer.futon.settings.history

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.databinding.ItemHistoryViewBinding
import io.github.landwarderer.futon.history.domain.HistoryViewMode
import com.google.android.material.R as materialR

private const val VIEWS_PER_ROW = 3

/** Picks the History tab's view from cards that each sketch it. */
class HistoryViewPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs) {

	var value: HistoryViewMode = HistoryViewMode.RECENT
		private set

	init {
		layoutResource = R.layout.preference_card_grid
		isSelectable = false
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		val name = getPersistedString(defaultValue as? String ?: HistoryViewMode.RECENT.name)
		value = HistoryViewMode.entries.find { it.name == name } ?: HistoryViewMode.RECENT
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		val grid = holder.itemView as LinearLayout
		grid.removeAllViews()
		val inflater = LayoutInflater.from(grid.context)
		val stroke = grid.resources.getDimensionPixelSize(materialR.dimen.m3_comp_outlined_card_outline_width) * 2
		for (row in ORDER.chunked(VIEWS_PER_ROW)) {
			val line = LinearLayout(grid.context)
			for (mode in row) {
				val item = ItemHistoryViewBinding.inflate(inflater, line, false)
				item.imageViewThumb.setImageResource(mode.thumbnail)
				item.textViewTitle.setText(mode.title)
				item.textViewSummary.setText(mode.summary)
				item.card.isChecked = mode == value
				item.card.strokeWidth = if (mode == value) stroke else 0
				item.card.setOnClickListener { select(mode) }
				line.addView(item.root)
			}
			// Keep the last row's cards the same width as the others.
			repeat(VIEWS_PER_ROW - row.size) {
				line.addView(
					android.view.View(grid.context),
					LinearLayout.LayoutParams(0, 0, 1f),
				)
			}
			grid.addView(line)
		}
	}

	private fun select(mode: HistoryViewMode) {
		if (mode == value || !callChangeListener(mode.name)) {
			return
		}
		value = mode
		persistString(mode.name)
		notifyChanged()
	}

	private companion object {

		/** Recent first, as the default. */
		val ORDER = listOf(
			HistoryViewMode.RECENT,
			HistoryViewMode.CALENDAR,
			HistoryViewMode.SHELF,
			HistoryViewMode.WHEEL,
			HistoryViewMode.LIST,
		)

		@get:StringRes
		val HistoryViewMode.title: Int
			get() = when (this) {
				HistoryViewMode.LIST -> R.string.history_view_list
				HistoryViewMode.RECENT -> R.string.history_view_recent
				HistoryViewMode.CALENDAR -> R.string.history_view_calendar
				HistoryViewMode.SHELF -> R.string.history_view_shelf
				HistoryViewMode.WHEEL -> R.string.history_view_wheel
			}

		@get:StringRes
		val HistoryViewMode.summary: Int
			get() = when (this) {
				HistoryViewMode.LIST -> R.string.history_view_list_summary
				HistoryViewMode.RECENT -> R.string.history_view_recent_summary
				HistoryViewMode.CALENDAR -> R.string.history_view_calendar_summary
				HistoryViewMode.SHELF -> R.string.history_view_shelf_summary
				HistoryViewMode.WHEEL -> R.string.history_view_wheel_summary
			}

		@get:DrawableRes
		val HistoryViewMode.thumbnail: Int
			get() = when (this) {
				HistoryViewMode.LIST -> R.drawable.thumb_history_list
				HistoryViewMode.RECENT -> R.drawable.thumb_history_recent
				HistoryViewMode.CALENDAR -> R.drawable.thumb_history_calendar
				HistoryViewMode.SHELF -> R.drawable.thumb_history_shelf
				HistoryViewMode.WHEEL -> R.drawable.thumb_history_wheel
			}
	}
}
