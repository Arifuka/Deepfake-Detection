package com.example.edgeguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.edgeguard.settings.AppPrefs
import com.example.edgeguard.settings.findActivity

private enum class Stage { Splash, Language, Permissions, Home }

/**
 * Decides which screen to show:
 *   Splash -> Language (first time only) -> Permissions (first time only) -> Home (the 3-tab app)
 * Returning users go Splash -> Home.
 */
@Composable
fun EdgeGuardRoot(onStart: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    var stage by rememberSaveable { mutableIntStateOf(Stage.Splash.ordinal) }

    when (Stage.entries[stage]) {
        Stage.Splash -> SplashScreen(onDone = {
            stage = when {
                AppPrefs.language(ctx) == null -> Stage.Language
                !AppPrefs.onboardingDone(ctx) -> Stage.Permissions
                else -> Stage.Home
            }.ordinal
        })

        Stage.Language -> LanguageScreen(onChosen = { lang ->
            AppPrefs.setLanguage(ctx, lang)
            stage = Stage.Permissions.ordinal
            // restart the screen so every text switches to the chosen language
            ctx.findActivity()?.recreate()
        })

        Stage.Permissions -> PermissionScreen(onAllDone = {
            AppPrefs.setOnboardingDone(ctx, true)
            stage = Stage.Home.ordinal
        })

        Stage.Home -> EdgeGuardApp(onStart = onStart, onStop = onStop)
    }
}
