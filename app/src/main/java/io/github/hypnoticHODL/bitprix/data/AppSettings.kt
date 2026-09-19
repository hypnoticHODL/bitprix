package io.github.hypnoticHODL.bitprix.data

import android.content.Context
import androidx.core.content.edit

/**
 * App-level (non widget-specific) user preferences.
 */
object AppSettings {
    private const val PREFS_NAME = "bitprix_app_prefs"
    private const val KEY_CURRENCY = "preferred_currency"
    private const val DEFAULT_CURRENCY = "usd"

    fun getCurrency(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CURRENCY, DEFAULT_CURRENCY)
            ?.lowercase()
            ?: DEFAULT_CURRENCY
    }

    fun setCurrency(context: Context, currency: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_CURRENCY, currency.lowercase())
        }
    }
}
