package com.example.edgeguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.edgeguard.settings.findActivity

/**
 * Sets the colour of the clock / battery icons at the top of the screen.
 *  darkIcons = true  -> dark icons (for white or light screens)
 *  darkIcons = false -> white icons (for navy or dark screens)
 * Goes back to the old setting when the screen is left.
 */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(darkIcons) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose { if (old != null) controller?.isAppearanceLightStatusBars = old }
    }
}
