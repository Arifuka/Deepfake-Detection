package com.example.edgeguard.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.edgeguard.settings.AppSettings

/**
 * Colours taken by eye from the Figma export. They are close, not exact.
 * To make them exact: in Figma click a shape > copy the hex from the Fill box, then paste here.
 *
 * The ones marked "follows dark mode" change by themselves when the user picks Dark mode.
 */
object EdgeGuardColors {
    val Navy = Color(0xFF0B1A33)        // bottom bar, headers, alert background
    val NavPill = Color(0xFFEAF2F9)     // selected tab pill
    val SwitchOffTrack = Color(0xFFA9A9A9)

    val Fake = Color(0xFFD71920)        // red
    val AiLabel = Color(0xFFFFC800)     // yellow
    val Safe = Color(0xFF2EF26B)        // green

    // ---- follow dark mode ----
    val Background: Color @Composable get() = if (AppSettings.dark) Color(0xFF0E1116) else Color.White
    val TextPrimary: Color @Composable get() = if (AppSettings.dark) Color(0xFFF2F4F8) else Color.Black
    val TextMuted: Color @Composable get() = if (AppSettings.dark) Color(0xFFA0A8B5) else Color(0xFF6B7280)
    val Surface: Color @Composable get() = if (AppSettings.dark) Color(0xFF232832) else Color(0xFFE6E9EE)
    val Divider: Color @Composable get() = if (AppSettings.dark) Color(0xFF2E3440) else Color(0xFFD9DCE1)
    val CardWarm: Color @Composable get() = if (AppSettings.dark) Color(0xFF3B3425) else Color(0xFFF8EFD6)
    val CardMint: Color @Composable get() = if (AppSettings.dark) Color(0xFF1F3A2B) else Color(0xFFD9F5DD)
    val Accent: Color @Composable get() = if (AppSettings.dark) Color(0xFF5B8FD6) else Navy   // switches, selected options

    // first-run screens
    val SplashBlue = Color(0xFF1B3A5E)  // splash background
    val OnboardNavy = Color(0xFF0A1730) // language screen background
    val OnboardCard = Color(0xFF1A2B4A) // language option cards and Continue button
    val OnboardNote = Color(0xFF9AA7BD) // "Disyorkan" / "Recommended"
}
