package io.github.hypnoticHODL.bitprix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.content.pm.ServiceInfo
import androidx.work.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import retrofit2.HttpException
import java.net.UnknownHostException
import io.github.hypnoticHODL.bitprix.R
import io.github.hypnoticHODL.bitprix.data.DataRepository
import io.github.hypnoticHODL.bitprix.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BitcoinWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BitcoinWidgetWorker", "doWork: Starting update")

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, BitcoinWidgetProvider::class.java)
        
        val inputIds = inputData.getIntArray(KEY_WIDGET_IDS)
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val appWidgetIds = if (inputIds != null && inputIds.isNotEmpty()) {
            inputIds
        } else {
            appWidgetManager.getAppWidgetIds(componentName)
        }

        for (appWidgetId in appWidgetIds) {
            updateWidget(appWidgetId, appWidgetManager, forceRefresh)
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "widget_refresh",
            "Widget Refresh",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, "widget_refresh")
            .setContentTitle("Refreshing Bitcoin Widget")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(
            1,
            notification,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private suspend fun updateWidget(appWidgetId: Int, appWidgetManager: AppWidgetManager, forceRefresh: Boolean) {
        if (forceRefresh) {
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_layout)
            loadingViews.setTextViewText(R.id.widget_price_text, "...")
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Apply settings
        val bgColor = WidgetSettingsManager.getBgColor(context, appWidgetId)
        val bgOpacity = WidgetSettingsManager.getBgOpacity(context, appWidgetId)
        val textColor = WidgetSettingsManager.getTextColor(context, appWidgetId)
        val textSize = WidgetSettingsManager.getTextSize(context, appWidgetId)
        val currency = WidgetSettingsManager.getCurrency(context, appWidgetId)

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
            action = BitcoinWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_icon, refreshPendingIntent)

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
        try {
            Log.d("BitcoinWidgetWorker", "Fetching price for widget $appWidgetId in $currency (force=$forceRefresh)")
            val response = DataRepository.getBitcoinPrice(context, currency, forceRefresh)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            if (response != null) {
                val price = response.getPrice(currency)
                val change = response.get24hChange(currency)
                
                val updateTime = if (response.lastFetchTime > 0) {
                    timeFormat.format(Date(response.lastFetchTime))
                } else if (forceRefresh) {
                    timeFormat.format(Date())
                } else {
                    context.getString(R.string.time_placeholder)
                }
                
                val formattedPrice = String.format(Locale.US, "%,.2f", price)
                val formattedChange = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                val changeColor = if (change >= 0) {
                    ContextCompat.getColor(context, R.color.price_up)
                } else {
                    ContextCompat.getColor(context, R.color.price_down)
                }

                views.setTextViewText(R.id.widget_pair_label, context.getString(R.string.widget_pair_format, currency.uppercase()))
                views.setTextViewText(R.id.widget_price_text, formattedPrice)
                views.setTextViewText(R.id.widget_change_label, formattedChange)
                views.setTextColor(R.id.widget_change_label, changeColor)
                views.setTextViewText(R.id.widget_time_label, updateTime)
            } else {
                views.setTextViewText(R.id.widget_price_text, context.getString(R.string.price_placeholder))
                views.setTextViewText(R.id.widget_time_label, timeFormat.format(Date()))
            }
        } catch (e: Exception) {
            Log.e("BitcoinWidgetWorker", "Error updating widget $appWidgetId", e)
            val errorText = when {
                e is HttpException && e.code() == 429 -> context.getString(R.string.error_429_short)
                e is UnknownHostException -> context.getString(R.string.error_no_connection_short)
                else -> context.getString(R.string.error_text)
            }
            views.setTextViewText(R.id.widget_price_text, errorText)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val WORK_NAME = "BitcoinWidgetUpdateWork"
        private const val KEY_WIDGET_IDS = "widget_ids"
        private const val KEY_FORCE_REFRESH = "force_refresh"

        fun enqueueWork(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BitcoinWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            var minInterval = 30 // Default 30 minutes
            
            if (appWidgetIds.isNotEmpty()) {
                var foundMin = Int.MAX_VALUE
                for (id in appWidgetIds) {
                    val interval = WidgetSettingsManager.getRefreshInterval(context, id)
                    if (interval < foundMin) {
                        foundMin = interval
                    }
                }
                if (foundMin != Int.MAX_VALUE) {
                    minInterval = foundMin
                }
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BitcoinWidgetWorker>(
                minInterval.toLong(), TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d("BitcoinWidgetWorker", "Periodic work enqueued with interval: $minInterval min")
        }

        fun enqueueOneTimeWork(context: Context, appWidgetIds: IntArray, forceRefresh: Boolean = false) {
            val data = Data.Builder()
                .putIntArray(KEY_WIDGET_IDS, appWidgetIds)
                .putBoolean(KEY_FORCE_REFRESH, forceRefresh)
                .build()

            val workRequestBuilder = OneTimeWorkRequestBuilder<BitcoinWidgetWorker>()
                .setInputData(data)
            
            if (forceRefresh) {
                workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            val workRequest = workRequestBuilder.build()

            // If it's a single widget (manual refresh), use unique work to avoid queuing
            if (appWidgetIds.size == 1 && forceRefresh) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ManualRefresh_${appWidgetIds[0]}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } else {
                WorkManager.getInstance(context).enqueue(workRequest)
            }
            Log.d("BitcoinWidgetWorker", "One-time work enqueued for widgets: ${appWidgetIds.joinToString()} (force=$forceRefresh)")
        }
        
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("BitcoinWidgetWorker", "Work cancelled")
        }
    }
}
