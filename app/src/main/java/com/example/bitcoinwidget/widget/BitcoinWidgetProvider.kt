package com.example.bitcoinwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.util.Log
import android.widget.RemoteViews
import com.example.bitcoinwidget.R
import com.example.bitcoinwidget.data.DataRepository
import com.example.bitcoinwidget.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BitcoinWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BitcoinWidgetProvider", "onReceive: ${intent.action}")
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("BitcoinWidgetProvider", "onEnabled: Enqueuing WorkManager")
        BitcoinWidgetWorker.enqueueWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("BitcoinWidgetProvider", "onDisabled: Cancelling WorkManager")
        BitcoinWidgetWorker.cancelWork(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("BitcoinWidgetProvider", "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetSettingsManager.deleteWidgetSettings(context, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Apply settings
        val bgColor = WidgetSettingsManager.getBgColor(context, appWidgetId)
        val bgOpacity = WidgetSettingsManager.getBgOpacity(context, appWidgetId)
        val textColor = WidgetSettingsManager.getTextColor(context, appWidgetId)
        val textSize = WidgetSettingsManager.getTextSize(context, appWidgetId)

        val colorWithOpacity = Color.argb(
            bgOpacity,
            Color.red(bgColor),
            Color.green(bgColor),
            Color.blue(bgColor)
        )

        views.setInt(R.id.widget_background, "setColorFilter", colorWithOpacity)
        views.setInt(R.id.widget_background, "setImageAlpha", bgOpacity)

        views.setTextColor(R.id.widget_pair_label, textColor)
        views.setTextColor(R.id.widget_price_text, textColor)
        views.setTextColor(R.id.widget_time_label, textColor)
        views.setInt(R.id.widget_refresh_icon, "setColorFilter", textColor)

        views.setTextViewTextSize(R.id.widget_price_text, TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())

        // Refresh Intent
        val refreshIntent = Intent(context, BitcoinWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_icon, refreshPendingIntent)

        // Initial update
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Intent to open MainActivity when clicking the widget
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        // Fetch price and update
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currency = WidgetSettingsManager.getCurrency(context, appWidgetId)
                Log.d("BitcoinWidgetProvider", "Fetching price for widget $appWidgetId in $currency")
                val response = DataRepository.getBitcoinPrice(context, currency)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                if (response != null) {
                    val price = response.getPrice(currency)
                    val change = response.get24hChange(currency)
                    
                    // Always use the lastFetchTime from the response to ensure accuracy
                    val updateTime = if (response.lastFetchTime > 0) {
                        timeFormat.format(Date(response.lastFetchTime))
                    } else {
                        "---" // Should not happen with current DataRepository implementation
                    }
                    
                    Log.d("BitcoinWidgetProvider", "Price for $currency: $price, Change: $change, Time: $updateTime")
                    val formattedPrice = String.format(Locale.US, "%,.2f", price)
                    val formattedChange = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                    val changeColor = if (change >= 0) Color.parseColor("#00C853") else Color.parseColor("#FF1744")

                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widget_pair_label, "Bitcoin ${currency.uppercase()}")
                        views.setTextViewText(R.id.widget_price_text, formattedPrice)
                        views.setTextViewText(R.id.widget_change_label, formattedChange)
                        views.setTextColor(R.id.widget_change_label, changeColor)
                        views.setTextViewText(R.id.widget_time_label, updateTime)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } else {
                    val currentTime = timeFormat.format(Date())
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widget_price_text, "---")
                        views.setTextViewText(R.id.widget_time_label, currentTime)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    views.setTextViewText(R.id.widget_price_text, "Error")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
