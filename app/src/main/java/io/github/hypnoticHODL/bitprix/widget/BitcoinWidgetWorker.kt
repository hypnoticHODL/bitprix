package io.github.hypnoticHODL.bitprix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.work.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
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

        // Run-local: deliberately not a field. Two worker runs can overlap (a periodic run and
        // a manual one), and a shared set would let one run's cleanup consume the other's ids.
        val stillLoading = mutableSetOf<Int>()

        try {
            for (appWidgetId in appWidgetIds) {
                updateWidget(appWidgetId, appWidgetManager, forceRefresh, stillLoading)
            }
        } finally {
            // A stopped or superseded run can leave a widget showing the "..." placeholder.
            // Schedule one cheap, non-forced pass to repaint those from cache so a widget can
            // never get stuck loading. On a clean run the set is empty and this is a no-op.
            if (stillLoading.isNotEmpty()) {
                Log.d("BitcoinWidgetWorker", "Run ended with ${stillLoading.size} widget(s) still loading; re-rendering")
                enqueueOneTimeWork(context, stillLoading.toIntArray(), forceRefresh = false, dedupe = false)
            }
        }

        return Result.success()
    }

    /**
     * This worker intentionally declares no getForegroundInfo(): expedited work is only
     * requested on Android 12+ (see enqueueOneTimeWork), where the platform runs it as an
     * expedited job rather than a foreground service. Below 12 WorkManager would have to start
     * a foreground service, which cannot legally run without a notification — and a
     * notification for a sub-second price fetch is noise, so we simply do not expedite there.
     */
    private suspend fun updateWidget(
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager,
        forceRefresh: Boolean,
        stillLoading: MutableSet<Int>
    ) {
        if (forceRefresh) {
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_layout)
            loadingViews.setTextViewText(R.id.widget_price_text, "...")
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)
            stillLoading.add(appWidgetId)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Apply settings
        val bgColor = WidgetSettingsManager.getBgColor(context, appWidgetId)
        val bgOpacity = WidgetSettingsManager.getBgOpacity(context, appWidgetId)
        val textColor = WidgetSettingsManager.getTextColor(context, appWidgetId)
        val textSize = WidgetSettingsManager.getTextSize(context, appWidgetId)
        val currency = WidgetSettingsManager.getCurrency(context, appWidgetId)

        // Tint with an opaque color; transparency is applied once via setImageAlpha.
        val tintColor = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))

        views.setInt(R.id.widget_background, "setColorFilter", tintColor)
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
                // The widget deliberately shows the 24h change (unlike the app, which follows
                // the selected timeframe) so the two surfaces can differ. Labelling it keeps
                // that difference legible rather than looking like a contradiction.
                val formattedChange = context.getString(
                    R.string.widget_change_24h_format,
                    String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                )
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
        } catch (e: CancellationException) {
            // A superseded (REPLACE'd) or stopped run must not paint an error onto the widget –
            // the newer run owns the final state. Rethrow so WorkManager sees the cancellation.
            Log.d("BitcoinWidgetWorker", "Update for widget $appWidgetId cancelled (superseded)")
            throw e
        } catch (e: Exception) {
            Log.e("BitcoinWidgetWorker", "Error updating widget $appWidgetId", e)
            // Friendly, widget-sized copy. Previously this leaked the raw HTTP status ("429")
            // into the price slot, which reads as a price rather than an error.
            val errorText = when {
                e is HttpException && e.code() == 429 -> context.getString(R.string.widget_error_rate_limited)
                e is UnknownHostException -> context.getString(R.string.widget_error_offline)
                else -> context.getString(R.string.widget_error_generic)
            }
            views.setTextViewText(R.id.widget_price_text, errorText)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        stillLoading.remove(appWidgetId)
    }

    companion object {
        private const val WORK_NAME = "BitcoinWidgetUpdateWork"
        private const val WORK_NAME_ONE_TIME = "BitcoinWidgetOneTimeUpdateWork"
        private const val KEY_WIDGET_IDS = "widget_ids"
        private const val KEY_FORCE_REFRESH = "force_refresh"

        fun enqueueWork(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BitcoinWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isEmpty()) {
                Log.d("BitcoinWidgetWorker", "No widgets found, skipping periodic work enqueue")
                cancelWork(context)
                return
            }

            var minInterval = 30 // Default 30 minutes
            
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

            // WorkManager minimum is 15 minutes
            val finalInterval = minInterval.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BitcoinWidgetWorker>(
                finalInterval.toLong(), TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d("BitcoinWidgetWorker", "Periodic work enqueued with interval: $finalInterval min (requested: $minInterval min)")
        }

        /**
         * @param dedupe when true (the default) this request REPLACES any in-flight one-time
         *   refresh, so overlapping requests cannot run concurrently and race to write the same
         *   widgets. Internal recovery runs pass false so they cannot cancel a newer, legitimate
         *   refresh that has already been enqueued.
         */
        fun enqueueOneTimeWork(
            context: Context,
            appWidgetIds: IntArray,
            forceRefresh: Boolean = false,
            dedupe: Boolean = true
        ) {
            val data = Data.Builder()
                .putIntArray(KEY_WIDGET_IDS, appWidgetIds)
                .putBoolean(KEY_FORCE_REFRESH, forceRefresh)
                .build()

            val workRequestBuilder = OneTimeWorkRequestBuilder<BitcoinWidgetWorker>()
                .setInputData(data)
            
            // Expedite only on Android 12+, where WorkManager uses the platform's expedited job.
            // Below 12 it would emulate this with a foreground service, which legally requires a
            // notification the user does not want; there we fall back to ordinary work.
            if (forceRefresh && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            val workRequest = workRequestBuilder.build()

            if (!dedupe) {
                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d("BitcoinWidgetWorker", "Recovery work enqueued for widgets: ${appWidgetIds.joinToString()}")
                return
            }

            // Manual single-widget refreshes are keyed per widget so that tapping one widget's
            // refresh does not cancel another widget's in-flight refresh. Every other one-time
            // refresh (system APPWIDGET_UPDATE, boot, resize) shares one key and therefore
            // supersedes the previous request.
            //
            // NOTE: these two keys are independent, so a manual refresh of widget A can still
            // run concurrently with a batch refresh that also covers A. That is tolerable: a
            // manual tap is also followed by the system's own APPWIDGET_UPDATE, so overlapping
            // refreshes are unavoidable here, and DataRepository de-duplicates the network call
            // (per-resource mutex + 5-minute memory cache) — the overlap costs a redundant
            // RemoteViews write, not a redundant API request.
            val uniqueName = if (appWidgetIds.size == 1 && forceRefresh) {
                "ManualRefresh_${appWidgetIds[0]}"
            } else {
                WORK_NAME_ONE_TIME
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("BitcoinWidgetWorker", "One-time work enqueued for widgets: ${appWidgetIds.joinToString()} (force=$forceRefresh)")
        }
        
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("BitcoinWidgetWorker", "Work cancelled")
        }
    }
}
