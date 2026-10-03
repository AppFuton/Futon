package io.github.landwarderer.futon.core.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavItem

/**
 * Compose-observable state for [BottomNavBar].
 *
 * MainActivity creates one instance and drives it; the ComposeView reads from it.
 * All fields are Compose state so the bar recomposes only when something actually changes.
 */
@Stable
class BottomNavBarState(
    settings: AppSettings,
    initialItems: List<NavItem> = settings.mainNavItems,
    initialSelectedId: Int = initialItems.firstOrNull()?.id ?: 0,
    showTipInitially: Boolean = settings.isTipEnabled(AppSettings.TIP_NEW_NAV_DEFAULT),
) {
    var navItems: List<NavItem> by mutableStateOf(initialItems)
    var selectedItemId: Int by mutableIntStateOf(initialSelectedId)
    var showTip: Boolean by mutableStateOf(showTipInitially)
    var isVisible: Boolean by mutableStateOf(true)

    /** Per-item badge counts.  Positive = number, negative = dot-only, absent/0 = none. */
    val badgeCounts: MutableMap<Int, Int> = mutableStateMapOf()

    fun setBadge(itemId: Int, count: Int) {
        if (count == 0) {
            badgeCounts.remove(itemId)
        } else {
            badgeCounts[itemId] = count
        }
    }
}
