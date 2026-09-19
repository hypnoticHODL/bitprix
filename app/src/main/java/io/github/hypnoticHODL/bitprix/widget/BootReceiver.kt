package io.github.hypnoticHODL.bitprix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        // Boot and app-update both need the periodic work re-armed. MY_PACKAGE_REPLACED is
        // what covers an update, which BOOT_COMPLETED alone would miss.
        Log.d("BootReceiver", "Received $action, rescheduling widget work")

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, BitcoinWidgetProvider::class.java)
        if (appWidgetManager.getAppWidgetIds(componentName).isEmpty()) {
            Log.d("BootReceiver", "No widgets placed, nothing to reschedule")
            return
        }

        BitcoinWidgetWorker.enqueueWork(context)
        BitcoinWidgetWorker.enqueueOneTimeWork(
            context,
            appWidgetManager.getAppWidgetIds(componentName),
            forceRefresh = false
        )
    }
}
