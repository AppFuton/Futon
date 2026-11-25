package io.github.landwarderer.futon.settings.nav

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.DockStyle
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.databinding.ItemDockStyleBinding

private const val STYLES_PER_ROW = 2

/** The settings the navigation bar previews are drawn with, apart from the style. */
data class NavBarLook(
	val tabs: List<NavItem>,
	val isCompact: Boolean,
	val isLabeled: Boolean,
	val isContinueEnabled: Boolean,
) {

	companion object {

		fun from(settings: AppSettings) = NavBarLook(
			tabs = settings.mainNavItems.filter {
				when (it) {
					NavItem.SUGGESTIONS -> settings.isSuggestionsEnabled
					NavItem.FEED -> settings.isTrackerEnabled
					else -> true
				}
			},
			isCompact = settings.isMinimalThemeEnabled,
			isLabeled = settings.isNavLabelsVisible,
			isContinueEnabled = settings.isMainFabEnabled,
		)
	}
}

/** The navigation bar as it will look, at full size. */
class NavBarPreviewPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs) {

	var style: DockStyle = DockStyle.TUFTED
		set(value) {
			if (field != value) {
				field = value
				notifyChanged()
			}
		}

	var look: NavBarLook? = null
		set(value) {
			if (field != value) {
				field = value
				notifyChanged()
			}
		}

	init {
		layoutResource = R.layout.preference_nav_bar_preview
		isSelectable = false
		isPersistent = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		val look = look ?: return
		holder.itemView.findViewById<NavBarPreviewView>(R.id.preview)
			.bind(style, look.tabs, look.isCompact, look.isLabeled, look.isContinueEnabled)
	}
}

/** Picks the bar's style from cards that each show it. */
class DockStylePreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs) {

	var value: DockStyle = DockStyle.TUFTED
		private set

	var look: NavBarLook? = null
		set(value) {
			if (field != value) {
				field = value
				notifyChanged()
			}
		}

	init {
		layoutResource = R.layout.preference_card_grid
		isSelectable = false
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		val name = getPersistedString(defaultValue as? String ?: DockStyle.TUFTED.name)
		value = DockStyle.entries.find { it.name == name } ?: DockStyle.TUFTED
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		val grid = holder.itemView as LinearLayout
		grid.removeAllViews()
		val inflater = LayoutInflater.from(grid.context)
		for (row in DockStyle.entries.chunked(STYLES_PER_ROW)) {
			val line = LinearLayout(grid.context)
			for (style in row) {
				val item = ItemDockStyleBinding.inflate(inflater, line, false)
				item.textViewTitle.setText(style.titleResId)
				item.textViewSummary.setText(style.summaryResId)
				item.card.isChecked = style == value
				item.card.strokeWidth = if (style == value) {
					grid.resources.getDimensionPixelSize(com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width) * 2
				} else {
					0
				}
				item.card.setOnClickListener { select(style) }
				look?.let { item.preview.bind(style, it.tabs, it.isCompact, it.isLabeled, it.isContinueEnabled) }
				line.addView(item.root)
			}
			grid.addView(line)
		}
	}

	private fun select(style: DockStyle) {
		if (style == value || !callChangeListener(style.name)) {
			return
		}
		value = style
		persistString(style.name)
		notifyChanged()
	}
}
