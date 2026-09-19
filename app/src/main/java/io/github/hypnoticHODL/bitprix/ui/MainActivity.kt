package io.github.hypnoticHODL.bitprix.ui

import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import android.view.MotionEvent
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.github.hypnoticHODL.bitprix.R
import io.github.hypnoticHODL.bitprix.data.AppSettings
import io.github.hypnoticHODL.bitprix.data.CurrencyCatalog
import io.github.hypnoticHODL.bitprix.data.DataRepository
import io.github.hypnoticHODL.bitprix.model.BitcoinPriceResponse
import io.github.hypnoticHODL.bitprix.widget.WidgetSettingsManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var tvPairLabel: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvChangePercent: TextView
    private lateinit var tvChangePeriod: TextView
    private lateinit var chart: LineChart
    private lateinit var fngGauge: FearAndGreedGauge
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvError: TextView
    private lateinit var btnRetry: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cgTimeframe: ChipGroup
    private lateinit var btnScreenshot: ImageButton
    private var currentCurrency: String = "usd"
    private var currencyOptions: List<String>? = null

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            saveChartScreenshot()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied_screenshot), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipe_refresh)
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvPairLabel = findViewById(R.id.tv_pair_label)
        tvPrice = findViewById(R.id.tv_bitcoin_price)
        tvChangePercent = findViewById(R.id.tv_change_percent)
        tvChangePeriod = findViewById(R.id.tv_change_period)
        chart = findViewById(R.id.chart_bitcoin)
        fngGauge = findViewById(R.id.fng_gauge)
        tvLastUpdate = findViewById(R.id.tv_last_update)
        tvError = findViewById(R.id.tv_error)
        btnRetry = findViewById(R.id.btn_retry)
        cgTimeframe = findViewById(R.id.cg_timeframe)
        btnScreenshot = findViewById(R.id.btn_screenshot)

        val initialCurrency = resolveInitialCurrency(intent)
        viewModel.setCurrency(initialCurrency)

        // Paint the pair label before any network work starts, so a failed load can never
        // expose the raw "@string/widget_pair_format" placeholder ("Bitcoin %1$s").
        renderPairLabel(initialCurrency)

        cgTimeframe.check(R.id.chip_1d)
        cgTimeframe.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val days = when (checkedId) {
                R.id.chip_1d -> 1
                R.id.chip_1w -> 7
                R.id.chip_1m -> 30
                R.id.chip_6m -> 180
                R.id.chip_1y -> 365
                else -> 1
            }
            viewModel.setTimeframe(days)
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.loadData(forceRefresh = true)
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot()
        }

        tvPairLabel.setOnClickListener {
            showCurrencyPicker()
        }

        btnRetry.setOnClickListener {
            viewModel.loadData(forceRefresh = true)
        }

        setupChart()
        observeViewModel()
        viewModel.loadData(forceRefresh = false)
    }

    /**
     * A widget tap can land here while we are already running, in which case the intent is
     * delivered here rather than to [onCreate]. Without this the app would silently ignore
     * the widget the user tapped and keep showing the previous currency.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val currency = resolveInitialCurrency(intent)
        if (!currency.equals(currentCurrency, ignoreCase = true)) {
            AppSettings.setCurrency(this, currency)
            viewModel.setCurrency(currency)
            viewModel.loadData(forceRefresh = false)
        }
    }

    private fun resolveInitialCurrency(intent: Intent?): String {
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        return if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetSettingsManager.getCurrency(this, appWidgetId)
        } else {
            AppSettings.getCurrency(this)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    swipeRefresh.isRefreshing = state.isLoading
                    currentCurrency = state.currency

                    renderPairLabel(state.currency)

                    val price = state.priceResponse
                    if (price != null) {
                        renderPrice(price, state.currency)
                    } else if (!state.isLoading) {
                        tvPrice.text = getString(R.string.price_unavailable)
                        tvLastUpdate.text = getString(R.string.last_update_never)
                    }

                    renderChange(state.changePercent, state.changePeriod)

                    state.fngResponse?.data?.firstOrNull()?.let {
                        fngGauge.setData(it.value.toIntOrNull() ?: 0, it.valueClassification)
                    }

                    if (state.chartUnavailable) {
                        chart.clear()
                        chart.invalidate()
                    }
                    state.displayChartData?.let { updateChart(it) }

                    renderError(state.errorKind)
                }
            }
        }
    }

    private fun renderPairLabel(currency: String) {
        val upper = currency.uppercase(Locale.US)
        tvPairLabel.text = getString(R.string.pair_label_with_selector, upper)
        tvPairLabel.contentDescription = getString(R.string.currency_selector_desc_format, upper)
    }

    private fun renderPrice(response: BitcoinPriceResponse, currency: String) {
        val hasPrice = response.hasPrice(currency)
        tvPrice.text = if (hasPrice) {
            String.format(Locale.US, "%,.2f", response.getPrice(currency))
        } else {
            getString(R.string.price_unavailable)
        }
        tvLastUpdate.text = formatUpdatedAt(response.lastFetchTime)
    }

    /**
     * Renders the headline percentage together with the period it actually describes.
     *
     * The period is supplied by the ViewModel from the data source that produced the value,
     * so the two can never drift. A null percentage renders as an explicit placeholder
     * rather than "0.00%", which would read as a real measurement.
     */
    private fun renderChange(percent: Double?, period: ChangePeriod) {
        val periodLabel = periodLabel(period)

        if (percent == null) {
            tvChangePercent.text = getString(R.string.change_unavailable)
            tvChangePercent.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            tvChangePeriod.visibility = View.INVISIBLE
            tvChangePercent.contentDescription = getString(R.string.change_unavailable)
            return
        }

        val formatted = String.format(
            Locale.US,
            getString(R.string.change_value_format),
            if (percent >= 0) "+" else "",
            percent
        )
        val color = ContextCompat.getColor(
            this,
            if (percent >= 0) R.color.price_up else R.color.price_down
        )

        tvChangePeriod.text = periodLabel
        tvChangePeriod.visibility = View.VISIBLE
        tvChangePercent.text = formatted
        tvChangePercent.setTextColor(color)
        tvChangePercent.contentDescription =
            getString(R.string.change_period_desc_format, periodLabel, formatted)
    }

    private fun periodLabel(period: ChangePeriod): String = when (period) {
        ChangePeriod.DAY_24H -> getString(R.string.change_period_24h)
        ChangePeriod.WEEK -> getString(R.string.timeframe_1w)
        ChangePeriod.MONTH -> getString(R.string.timeframe_1m)
        ChangePeriod.SIX_MONTHS -> getString(R.string.timeframe_6m)
        ChangePeriod.YEAR -> getString(R.string.timeframe_1y)
    }

    /**
     * A bare "HH:mm" cannot express staleness — data from a previous day looked current.
     * Anything older than a day is labelled as such.
     */
    private fun formatUpdatedAt(timestamp: Long): String {
        if (timestamp <= 0) return getString(R.string.last_update_never)
        val ageMs = System.currentTimeMillis() - timestamp
        val time = timeFormat.format(Date(timestamp))
        return if (ageMs < DAY_MS) {
            getString(R.string.updated_at_format, time)
        } else {
            getString(R.string.updated_yesterday_format, time)
        }
    }

    private fun renderError(kind: ErrorKind?) {
        if (kind == null) {
            tvError.visibility = View.GONE
            btnRetry.visibility = View.GONE
            return
        }
        tvError.text = when (kind) {
            ErrorKind.OFFLINE -> getString(R.string.error_offline)
            ErrorKind.RATE_LIMIT -> getString(R.string.error_rate_limit_friendly)
            ErrorKind.SERVER -> getString(R.string.error_server)
            ErrorKind.UNKNOWN -> getString(R.string.error_unknown)
        }
        tvError.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setHighlightPerTapEnabled(true)
        chart.setHighlightPerDragEnabled(true)
        chart.isDragEnabled = true
        chart.isScaleXEnabled = false
        chart.isScaleYEnabled = false
        chart.setDragDecelerationEnabled(false)
        chart.setDrawGridBackground(false)

        val marker = ChartMarkerView(this, R.layout.chart_marker_view)
        marker.chartView = chart
        chart.marker = marker

        chart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                swipeRefresh.isEnabled = false
                chart.parent?.requestDisallowInterceptTouchEvent(true)
            }

            override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                swipeRefresh.isEnabled = true
                chart.parent?.requestDisallowInterceptTouchEvent(false)
            }

            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {
                if (me == null) return
                val lastHighlight = chart.highlighted?.firstOrNull() ?: return
                val markerView = chart.marker ?: return

                val markerAsView = markerView as? View ?: return

                val x = lastHighlight.xPx
                val y = lastHighlight.yPx
                val offset = markerView.getOffsetForDrawingAtPoint(x, y)

                val left = x + offset.x
                val top = y + offset.y
                val right = left + markerAsView.width
                val bottom = top + markerAsView.height

                if (me.x in left..right && me.y in top..bottom) {
                    chart.highlightValue(null)
                }
            }

            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
        }

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {}
            override fun onNothingSelected() {}
        })

        val xAxis = chart.xAxis
        xAxis.setDrawGridLines(false)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = ContextCompat.getColor(this, R.color.chart_axis_label)
        xAxis.setDrawLabels(true)
        xAxis.setLabelCount(4, false)
        xAxis.granularity = 1f

        val yAxis = chart.axisLeft
        yAxis.setDrawGridLines(false)
        yAxis.textColor = ContextCompat.getColor(this, R.color.chart_axis_label)
        yAxis.setDrawLabels(true)
        yAxis.setSpaceTop(10f)
        yAxis.setSpaceBottom(10f)
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val symbol = getCurrencySymbol()
                return when {
                    value >= 10_000_000f -> String.format(Locale.US, "%s%.1fM", symbol, value / 1_000_000f)
                    value >= 100_000f -> String.format(Locale.US, "%s%.2fM", symbol, value / 1_000_000f)
                    value >= 1_000f -> String.format(Locale.US, "%s%.2fK", symbol, value / 1_000f)
                    else -> symbol + value.toInt().toString()
                }
            }
        }

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        // Provide enough offsets for Y-axis labels and top rendering.
        chart.setExtraOffsets(25f, 10f, 10f, 10f)
    }

    /**
     * Renders the chart only. The headline percentage is owned by the ViewModel and rendered
     * by [renderChange] — this method deliberately does not touch it, otherwise the chart
     * would overwrite the 24h figure with a timeframe figure (or vice versa).
     */
    private fun updateChart(prices: List<List<Double>>) {
        if (prices.isEmpty()) return

        val entries = prices.mapIndexed { index, list ->
            Entry(index.toFloat(), list[1].toFloat(), list[0].toLong())
        }

        val dataSet = LineDataSet(entries, getString(R.string.chart_label_btc_price, currentCurrency.uppercase()))
        val btcOrange = ContextCompat.getColor(this, R.color.bitcoin_orange)
        dataSet.color = btcOrange
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2.5f
        dataSet.setDrawFilled(true)

        // Use a semi-transparent orange for the area under the curve
        dataSet.fillColor = btcOrange
        dataSet.fillAlpha = 40

        // Smoothing the line
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        chart.data = LineData(dataSet)

        val timeframe = viewModel.uiState.value.timeframe

        // Update X-axis labels
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            private val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                if (index >= 0 && index < prices.size) {
                    val timestamp = prices[index].getOrNull(0)?.toLong() ?: return ""
                    return if (timeframe == 1) {
                        hourFormat.format(Date(timestamp))
                    } else {
                        dayFormat.format(Date(timestamp))
                    }
                }
                return ""
            }
        }

        // Auto-scale the Y-axis to fit the filtered data range
        chart.axisLeft.resetAxisMinimum()
        chart.axisLeft.resetAxisMaximum()

        chart.notifyDataSetChanged()
        chart.invalidate()

        chart.contentDescription = getString(
            R.string.chart_desc_format,
            getString(R.string.chart_label_btc_price, currentCurrency.uppercase())
        )
    }

    private fun saveChartScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        val container = chart.parent as? View ?: return
        if (container.width <= 0 || container.height <= 0) {
            // createBitmap would throw on a zero-sized view; fail loudly instead of silently.
            Toast.makeText(this, getString(R.string.chart_save_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        container.draw(canvas)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = openScreenshotStream() ?: throw IOException("No screenshot stream available")
                stream.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.chart_saved), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.chart_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Opens the destination for the exported chart, or null when the platform refuses.
     *
     * Returning null (rather than silently skipping the write) is what makes the failure
     * toast reachable — previously a null MediaStore Uri produced no feedback at all.
     */
    private fun openScreenshotStream(): OutputStream? {
        val fileName = getString(R.string.screenshot_filename_prefix) + "${System.currentTimeMillis()}.png"
        val folderName = getString(R.string.screenshot_folder_name)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folderName")
            }
            val imageUri: Uri = contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            contentResolver.openOutputStream(imageUri)
        } else {
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString() + "/$folderName"
            val dir = File(imagesDir)
            if (!dir.exists()) dir.mkdirs()
            val image = File(dir, fileName)

            // Scan the file so it appears in the gallery
            MediaScannerConnection.scanFile(
                this,
                arrayOf(image.absolutePath),
                arrayOf("image/png"),
                null
            )
            FileOutputStream(image)
        }
    }

    private fun showCurrencyPicker() {
        val cached = currencyOptions
        if (cached != null) {
            showCurrencyDialog(cached)
            return
        }
        lifecycleScope.launch {
            val currencies = withContext(Dispatchers.IO) {
                DataRepository.getSupportedCurrencies(this@MainActivity)
            }
            val options = CurrencyCatalog.filterSupported(currencies)
            currencyOptions = options
            showCurrencyDialog(options)
        }
    }

    private fun showCurrencyDialog(currencies: List<String>) {
        val selectedIndex = currencies.indexOfFirst { it.equals(currentCurrency, ignoreCase = true) }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.label_target_currency)
            .setSingleChoiceItems(currencies.toTypedArray(), selectedIndex) { dialog, which ->
                dialog.dismiss()
                val selected = currencies[which].lowercase()
                if (selected != currentCurrency) {
                    AppSettings.setCurrency(this, selected)
                    viewModel.setCurrency(selected)
                    viewModel.loadData(forceRefresh = false)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)

        val dialog = builder.create()

        // A 46-item single-choice list restores a stale scroll offset of its own accord, so
        // the currently selected row (and its tick) could sit off-screen — the dialog looked
        // like it had no selection at all. Scroll to it explicitly once the list is laid out.
        dialog.setOnShowListener {
            if (selectedIndex >= 0) {
                (dialog as? AlertDialog)?.listView?.post {
                    dialog.listView?.setSelection(selectedIndex)
                }
            }
        }
        dialog.show()
    }

    private fun getCurrencySymbol(): String {
        val currencyMap = mapOf(
            "btc" to "₿", "usd" to "$", "aed" to "د.إ", "ars" to "$", "aud" to "A$",
            "bdt" to "৳", "bhd" to "د.ك", "bmd" to "BD", "brl" to "R$", "cad" to "C$",
            "chf" to "CHF", "clp" to "$", "cny" to "¥", "czk" to "Kč", "dkk" to "kr",
            "eur" to "€", "gbp" to "£", "gel" to "₾", "hkd" to "HK$", "huf" to "Ft",
            "idr" to "Rp", "ils" to "₪", "inr" to "₹", "jpy" to "¥", "krw" to "₩",
            "kwd" to "د.ك", "lkr" to "Rs", "mmk" to "K", "mxn" to "MX$", "myr" to "RM",
            "ngn" to "₦", "nok" to "kr", "nzd" to "NZ$", "php" to "₱", "pkr" to "₨",
            "pln" to "zł", "rub" to "₽", "sar" to "﷼", "sek" to "kr", "sgd" to "S$",
            "thb" to "฿", "try" to "₺", "twd" to "NT$", "uah" to "₴", "vef" to "Bs",
            "vnd" to "₫", "zar" to "R"
        )
        return currencyMap[currentCurrency.lowercase()] ?: (currentCurrency.uppercase() + " ")
    }

    inner class ChartMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
        private val tvDate: TextView = findViewById(R.id.tv_marker_date)
        private val tvPrice: TextView = findViewById(R.id.tv_marker_price)
        private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            if (e == null) return
            val timestamp = e.data as? Long ?: 0L
            val timeframe = viewModel.uiState.value.timeframe

            tvDate.text = if (timeframe == 1) {
                hourFormat.format(Date(timestamp))
            } else {
                dayFormat.format(Date(timestamp))
            }

            val formattedPrice = String.format(Locale.US, "%,.2f", e.y)
            tvPrice.text = formattedPrice

            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF(-(width / 2).toFloat(), -height.toFloat() - 10f)
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
