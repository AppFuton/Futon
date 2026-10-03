package io.github.landwarderer.futon.core.prefs

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import io.github.landwarderer.futon.R

@Keep
enum class NavItem(
	@IdRes val id: Int,
	@StringRes val title: Int,
	/** Selector drawable used by the legacy NavigationBarView / NavigationRailView. */
	@DrawableRes val icon: Int,
	/** Plain vector shown in Compose when this item IS selected. */
	@DrawableRes val iconSelected: Int,
	/** Plain vector shown in Compose when this item is NOT selected. */
	@DrawableRes val iconUnselected: Int,
	/** When true, tapping this item opens an Activity instead of swapping a Fragment. */
	val isActivityLauncher: Boolean = false,
) {

	HISTORY(
		id = R.id.nav_history,
		title = R.string.history,
		icon = R.drawable.ic_history_selector,
		iconSelected = R.drawable.ic_history,
		iconUnselected = R.drawable.ic_history,
	),
	FAVORITES(
		id = R.id.nav_favorites,
		title = R.string.favourites,
		icon = R.drawable.ic_favourites_selector,
		iconSelected = R.drawable.ic_heart,
		iconUnselected = R.drawable.ic_heart_outline,
	),
	LOCAL(
		id = R.id.nav_local,
		title = R.string.on_device,
		icon = R.drawable.ic_storage_selector,
		iconSelected = R.drawable.ic_storage_checked,
		iconUnselected = R.drawable.ic_storage,
	),
	EXPLORE(
		id = R.id.nav_explore,
		title = R.string.explore,
		icon = R.drawable.ic_explore_selector,
		iconSelected = R.drawable.ic_explore_checked,
		iconUnselected = R.drawable.ic_explore_normal,
	),
	SUGGESTIONS(
		id = R.id.nav_suggestions,
		title = R.string.suggestions,
		icon = R.drawable.ic_suggestion_selector,
		iconSelected = R.drawable.ic_suggestion_checked,
		iconUnselected = R.drawable.ic_suggestion,
	),
	FEED(
		id = R.id.nav_feed,
		title = R.string.feed,
		icon = R.drawable.ic_feed_selector,
		iconSelected = R.drawable.ic_feed,
		iconUnselected = R.drawable.ic_feed,
	),
	UPDATED(
		id = R.id.nav_updated,
		title = R.string.updated,
		icon = R.drawable.ic_updated_selector,
		iconSelected = R.drawable.ic_updated_checked,
		iconUnselected = R.drawable.ic_updated,
	),
	BOOKMARKS(
		id = R.id.nav_bookmarks,
		title = R.string.bookmarks,
		icon = R.drawable.ic_bookmark_selector,
		iconSelected = R.drawable.ic_bookmark_checked,
		iconUnselected = R.drawable.ic_bookmark,
	),
	SETTINGS(
		id = R.id.nav_settings,
		title = R.string.settings,
		icon = R.drawable.ic_settings_filled,
		iconSelected = R.drawable.ic_settings_filled,
		iconUnselected = R.drawable.ic_settings,
		isActivityLauncher = true,
	),
	;

	fun isAvailable(settings: AppSettings): Boolean = when (this) {
		SUGGESTIONS -> settings.isSuggestionsEnabled
		UPDATED, FEED -> settings.isTrackerEnabled
		else -> true
	}
}
