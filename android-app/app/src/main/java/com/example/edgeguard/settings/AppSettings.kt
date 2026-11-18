package com.example.edgeguard.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.edgeguard.R
import com.example.edgeguard.ui.theme.InterFamily

/**
 * Live look-and-feel settings (dark mode, text size, font).
 * The screens read these values, so when one changes the whole app redraws at once.
 * Values are saved with AppPrefs and loaded when the app starts (see MainActivity).
 */
object AppSettings {
    var dark by mutableStateOf(false)
        private set
    var fontStep by mutableIntStateOf(1)
        private set
    var fontKey by mutableStateOf("inter")
        private set

    val fontScale: Float get() = AppPrefs.FONT_SCALES[fontStep]

    private val atkinson = FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_regular, FontWeight.Medium),
        Font(R.font.atkinson_bold, FontWeight.Bold),
        Font(R.font.atkinson_bold, FontWeight.ExtraBold)
    )

    val fontFamily: FontFamily
        get() = when (fontKey) {
            "atkinson" -> atkinson
            "system" -> FontFamily.Default
            else -> InterFamily
        }

    fun load(c: Context) {
        dark = AppPrefs.dark(c)
        fontStep = AppPrefs.fontStep(c)
        fontKey = AppPrefs.fontKey(c)
    }

    fun setDark(c: Context, v: Boolean) { dark = v; AppPrefs.setDark(c, v) }
    fun setFontStep(c: Context, v: Int) { fontStep = v; AppPrefs.setFontStep(c, v) }
    fun setFontKey(c: Context, v: String) { fontKey = v; AppPrefs.setFontKey(c, v) }
}
