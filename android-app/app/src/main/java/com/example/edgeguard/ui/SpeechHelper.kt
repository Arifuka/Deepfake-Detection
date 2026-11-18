package com.example.edgeguard.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Read-aloud helper (the speaker icons in the design).
 * Uses the phone's own text-to-speech engine, so it works offline.
 *
 * Needs this in AndroidManifest.xml (Android 11+), just before <application>:
 *   <queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>
 */
class SpeechHelper(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, locale: Locale) {
        if (!ready) return
        tts.setLanguage(locale) // if that voice is missing the phone keeps its old voice; never crashes
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "edgeguard-read")
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }
    }
}

@Composable
fun rememberSpeech(): SpeechHelper {
    val ctx = LocalContext.current
    val helper = remember { SpeechHelper(ctx) }
    DisposableEffect(Unit) { onDispose { helper.shutdown() } }
    return helper
}
