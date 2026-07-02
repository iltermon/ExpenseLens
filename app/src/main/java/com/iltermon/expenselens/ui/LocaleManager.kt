package com.iltermon.expenselens.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the user's chosen UI language at the Activity level (via attachBaseContext), so the whole
 * activity's resources resolve in that language and the Activity stays a real Activity (unlike
 * overriding Compose's LocalContext, which breaks Activity lookups such as the result registry).
 *
 * Stored in SharedPreferences rather than Room so it can be read synchronously before the database
 * is available. A blank tag means "follow the device language".
 */
object LocaleManager {
    private const val PREFS = "expenselens_prefs"
    private const val KEY_LANGUAGE = "language_tag"

    fun getLanguageTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "").orEmpty()

    fun setLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, tag).commit()
    }

    /** Wraps [base] so its resources resolve in the saved language (blank = device default). */
    fun wrap(base: Context): Context {
        val tag = getLanguageTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
