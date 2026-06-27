package io.github.hypnoticHODL.bitprix.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log

class BitcoinWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "io.github.hypnoticHODL.bitprix.ACTION_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BitcoinWidgetProvider", "onReceive: ${intent.action}")
        if (intent.action == ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.d("BitcoinWidgetProvider", "Manual refresh requested for widget $appWidgetId")
                BitcoinWidgetWorker.enqueueOneTimeWork(context, intArrayOf(appWidgetId), forceRefresh = true)
            }
        }
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
        Log.d("BitcoinWidgetProvider", "onUpdate called for ${appWidgetIds.size} widgets. Enqueuing one-time update.")
        BitcoinWidgetWorker.enqueueOneTimeWork(context, appWidgetIds, forceRefresh = true)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetSettingsManager.deleteWidgetSettings(context, appWidgetId)
        }
    }
}
