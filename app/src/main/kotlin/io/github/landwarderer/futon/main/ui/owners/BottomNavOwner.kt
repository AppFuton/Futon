package io.github.landwarderer.futon.main.ui.owners

import androidx.compose.ui.platform.ComposeView
import io.github.landwarderer.futon.core.ui.components.BottomNavBarState

interface BottomNavOwner {

    /** The ComposeView hosting the bottom nav bar, or null on wide-layout screens. */
    val bottomNavView: ComposeView?

    /** Compose state that drives the bottom nav bar. Null when [bottomNavView] is null. */
    val bottomNavState: BottomNavBarState?
}
