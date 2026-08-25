package io.github.landwarderer.futon.core.ui.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.landwarderer.futon.core.util.ext.isNightMode

/**
 * Minimal Compose theme wrapper for View-Compose interop.
 *
 * Uses Material3 dynamic colors on Android 12+ to match the system/app palette,
 * and falls back to the default Material3 color scheme on older devices.
 * Typography and shapes are left at Material3 defaults.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = remember(context) { context.resources.isNightMode }

    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) androidx.compose.material3.darkColorScheme()
        else androidx.compose.material3.lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
