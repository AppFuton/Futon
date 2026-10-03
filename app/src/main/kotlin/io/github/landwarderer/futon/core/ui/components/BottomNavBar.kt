package io.github.landwarderer.futon.core.ui.components

import androidx.annotation.IdRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavItem

/**
 * Compose bottom navigation bar.
 *
 * @param navItems       Ordered list of items to display (from [AppSettings.mainNavItems]).
 * @param selectedItemId Resource id of the currently selected item, or 0 if none.
 * @param badgeCounts    Map from item resource id to badge count.
 *                       Positive = number badge, negative = dot-only badge, 0/absent = no badge.
 * @param showTip        Whether to show the one-time "new defaults" tutorial tip.
 * @param onItemClick    Called when the user taps an item. Receives the [NavItem].
 * @param onTipDismiss   Called when the user dismisses the tutorial tip.
 */
@Composable
fun BottomNavBar(
    navItems: List<NavItem>,
    @IdRes selectedItemId: Int,
    badgeCounts: Map<Int, Int> = emptyMap(),
    showTip: Boolean = false,
    onItemClick: (NavItem) -> Unit = {},
    onTipDismiss: () -> Unit = {},
) {
    Column {
        AnimatedVisibility(
            visible = showTip,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            NavBarTutorialTip(onDismiss = onTipDismiss)
        }

        NavigationBar {
            navItems.forEach { item ->
                val count = badgeCounts[item.id] ?: 0
                val selected = item.id == selectedItemId
                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemClick(item) },
                    icon = {
                        val iconRes = if (selected) item.iconSelected else item.iconUnselected
                        BadgedBox(
                            badge = {
                                when {
                                    count > 0 -> Badge { Text(count.toString()) }
                                    count < 0 -> Badge()
                                    else -> Unit
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = stringResource(item.title),
                            )
                        }
                    },
                    label = { Text(stringResource(item.title)) },
                )
            }
        }
    }
}

/**
 * One-time tutorial tip shown below the top of the nav bar area, informing the user that
 * Futon has a new set of default navigation items.  Dismissed permanently on tap.
 */
@Composable
private fun NavBarTutorialTip(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.tip_new_nav_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(stringResource(R.string.got_it))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBottomNavBar() {
    BottomNavBar(
        navItems = AppSettings.FUTON_DEFAULT_NAV_ITEMS,
        selectedItemId = AppSettings.FUTON_DEFAULT_NAV_ITEMS.first().id,
        badgeCounts = mapOf(AppSettings.FUTON_DEFAULT_NAV_ITEMS[2].id to 3),
        showTip = false,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewBottomNavBarWithTip() {
    BottomNavBar(
        navItems = AppSettings.FUTON_DEFAULT_NAV_ITEMS,
        selectedItemId = AppSettings.FUTON_DEFAULT_NAV_ITEMS.first().id,
        showTip = true,
    )
}
