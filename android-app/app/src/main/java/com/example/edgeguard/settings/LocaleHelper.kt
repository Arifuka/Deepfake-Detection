package com.example.edgeguard.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import java.util.Locale

/**
 * Lets the app use the language the user picked on the language screen
 * (no extra library needed).
 *
 *  - MainActivity calls wrap() in attachBaseContext, so every stringResource() in the UI
 *    uses the chosen language. After a change, call activity.recreate().
 *  - Services (overlay, notifications) should read their texts with wrap(context, lang) too.
 */
object LocaleHelper {

    /** Context whose resources use [lang], and also sets the default locale. */
    fun wrap(base: Context, lang: String?): Context {
        if (lang.isNullOrEmpty()) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Like wrap() but does not touch the default locale. Handy to read one string in another language. */
    fun localized(base: Context, lang: String): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return base.createConfigurationContext(config)
    }
}

/** Finds the Activity behind a Compose LocalContext. */
tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
