package com.example.edgeguard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours taken by eye from the Figma export. They are close, not exact.
 * To make them exact: in Figma click a shape > copy the hex from the Fill box, then paste here.
 */
object EdgeGuardColors {
    val Navy = Color(0xFF0B1A33)        // bottom bar, headers, alert background
    val NavPill = Color(0xFFEAF2F9)     // selected tab pill
    val TextPrimary = Color(0xFF000000)
    val TextMuted = Color(0xFF6B7280)   // "EdgeGuard is currently not active"
    val SwitchOffTrack = Color(0xFFA9A9A9)

    val Fake = Color(0xFFD71920)        // red
    val AiLabel = Color(0xFFFFC800)     // yellow
    val Safe = Color(0xFF2EF26B)        // green
}
