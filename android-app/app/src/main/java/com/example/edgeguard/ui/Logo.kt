package com.example.edgeguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.edgeguard.R
import com.example.edgeguard.settings.AppSettings

/**
 * The ONE place the logo is drawn (res/drawable-nodpi/edgeguard_logo.png, a transparent PNG).
 * It is drawn black on light screens and white on dark screens
 * (onDark = true for screens that are always dark, like the splash; dark mode is picked up by itself).
 */
@Composable
fun EdgeGuardLogo(size: Dp, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val white = onDark || AppSettings.dark
    Image(
        painter = painterResource(R.drawable.edgeguard_logo),
        contentDescription = "EdgeGuard",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(if (white) Color.White else Color.Black),
        modifier = modifier.size(size)
    )
}
