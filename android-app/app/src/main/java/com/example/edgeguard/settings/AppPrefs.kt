package com.example.edgeguard.settings

import android.content.Context

/**
 * Small settings store (SharedPreferences, private to the app, never uploaded).
 *  - language: "ms" (Bahasa Melayu) or "en" (English). null = the user has not chosen yet.
 *  - onboardingDone: true after the first-run permission screen is finished.
 *  - dark: dark screen mode on/off
 *  - fontStep: text size, 0 small, 1 normal, 2 large, 3 extra large
 *  - fontKey: "inter", "atkinson" or "system"
 */
object AppPrefs {
    private const val FILE = "edgeguard_prefs"
    private const val KEY_LANG = "language"
    private const val KEY_DONE = "onboarding_done"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_FONT_STEP = "font_step"
    private const val KEY_FONT = "font_key"

    /** Multiplier for each text size step. */
    val FONT_SCALES = floatArrayOf(0.9f, 1.0f, 1.2f, 1.4f)

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun language(c: Context): String? = sp(c).getString(KEY_LANG, null)

    fun setLanguage(c: Context, lang: String) {
        sp(c).edit().putString(KEY_LANG, lang).commit()
    }

    fun onboardingDone(c: Context): Boolean = sp(c).getBoolean(KEY_DONE, false)

    fun setOnboardingDone(c: Context, done: Boolean) {
        sp(c).edit().putBoolean(KEY_DONE, done).apply()
    }

    fun dark(c: Context): Boolean = sp(c).getBoolean(KEY_DARK, false)
    fun setDark(c: Context, v: Boolean) { sp(c).edit().putBoolean(KEY_DARK, v).apply() }

    fun fontStep(c: Context): Int = sp(c).getInt(KEY_FONT_STEP, 1).coerceIn(0, FONT_SCALES.size - 1)
    fun setFontStep(c: Context, v: Int) { sp(c).edit().putInt(KEY_FONT_STEP, v).apply() }

    fun fontKey(c: Context): String = sp(c).getString(KEY_FONT, "inter") ?: "inter"
    fun setFontKey(c: Context, v: String) { sp(c).edit().putString(KEY_FONT, v).apply() }
}
