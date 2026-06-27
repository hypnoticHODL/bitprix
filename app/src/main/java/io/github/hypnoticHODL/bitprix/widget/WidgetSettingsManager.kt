package io.github.hypnoticHODL.bitprix.widget

import android.content.Context
import android.graphics.Color

object WidgetSettingsManager {
    private const val PREFS_NAME = "io.github.hypnoticHODL.bitprix.widget.prefs"
    private const val PREFIX_BG_COLOR = "bg_color_"
    private const val PREFIX_BG_OPACITY = "bg_opacity_"
    private const val PREFIX_TEXT_COLOR = "text_color_"
    private const val PREFIX_TEXT_SIZE = "text_size_"
    private const val PREFIX_CURRENCY = "currency_"
    private const val PREFIX_REFRESH_INTERVAL = "refresh_interval_"

    fun saveWidgetSettings(
        context: Context,
        appWidgetId: Int,
        bgColor: Int,
        bgOpacity: Int,
        textColor: Int,
        textSize: Int,
        currency: String,
        refreshInterval: Int
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(PREFIX_BG_COLOR + appWidgetId, bgColor)
            putInt(PREFIX_BG_OPACITY + appWidgetId, bgOpacity)
            putInt(PREFIX_TEXT_COLOR + appWidgetId, textColor)
            putInt(PREFIX_TEXT_SIZE + appWidgetId, textSize)
            putString(PREFIX_CURRENCY + appWidgetId, currency)
            putInt(PREFIX_REFRESH_INTERVAL + appWidgetId, refreshInterval)
            apply()
        }
    }

    fun getBgColor(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREFIX_BG_COLOR + appWidgetId, Color.WHITE)
    }

    fun getBgOpacity(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREFIX_BG_OPACITY + appWidgetId, 255) // Default full opaque
    }

    fun getTextColor(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREFIX_TEXT_COLOR + appWidgetId, Color.BLACK)
    }

    fun getTextSize(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREFIX_TEXT_SIZE + appWidgetId, 22)
    }

    fun getCurrency(context: Context, appWidgetId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREFIX_CURRENCY + appWidgetId, "usd") ?: "usd"
    }

    fun getRefreshInterval(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREFIX_REFRESH_INTERVAL + appWidgetId, 30) // Default 30 min
    }

    fun deleteWidgetSettings(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            remove(PREFIX_BG_COLOR + appWidgetId)
            remove(PREFIX_BG_OPACITY + appWidgetId)
            remove(PREFIX_TEXT_COLOR + appWidgetId)
            remove(PREFIX_TEXT_SIZE + appWidgetId)
            remove(PREFIX_CURRENCY + appWidgetId)
            remove(PREFIX_REFRESH_INTERVAL + appWidgetId)
            apply()
        }
    }
}
