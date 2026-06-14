package com.example.bitcoinwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class BitcoinWidgetWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BitcoinWidgetWorker", "doWork: Starting background update")
        
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, BitcoinWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        val intent = android.content.Intent(applicationContext, BitcoinWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        
        applicationContext.sendBroadcast(intent)
        
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "BitcoinWidgetUpdateWork"

        fun enqueueWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BitcoinWidgetWorker>(
                30, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d("BitcoinWidgetWorker", "Work enqueued")
        }
        
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("BitcoinWidgetWorker", "Work cancelled")
        }
    }
}
