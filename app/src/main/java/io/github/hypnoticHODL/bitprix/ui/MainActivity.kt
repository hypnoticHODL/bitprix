package io.github.hypnoticHODL.bitprix.ui

import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import android.view.MotionEvent
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.net.UnknownHostException
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.github.hypnoticHODL.bitprix.R
import io.github.hypnoticHODL.bitprix.data.DataRepository
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvPairLabel: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvChangePercent: TextView
    private lateinit var chart: LineChart
    private lateinit var fngGauge: FearAndGreedGauge
    private lateinit var tvLastUpdate: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cgTimeframe: ChipGroup
    private lateinit var btnScreenshot: ImageButton
    private var currentCurrency: String = "usd"
    private var currentDays: Int = 1
    private var fullYearChartData: List<List<Double>>? = null
    private var oneDayChartData: List<List<Double>>? = null
    
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

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        currentCurrency = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetSettingsManager.getCurrency(this, appWidgetId)
        } else {
            "usd"
        }

        tvPairLabel = findViewById(R.id.tv_pair_label)
        tvPrice = findViewById(R.id.tv_bitcoin_price)
        tvChangePercent = findViewById(R.id.tv_change_percent)
        chart = findViewById(R.id.chart_bitcoin)
        fngGauge = findViewById(R.id.fng_gauge)
        tvLastUpdate = findViewById(R.id.tv_last_update)
        cgTimeframe = findViewById(R.id.cg_timeframe)
        btnScreenshot = findViewById(R.id.btn_screenshot)

        cgTimeframe.check(R.id.chip_1d)
        cgTimeframe.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentDays = when (checkedId) {
                R.id.chip_1d -> 1
                R.id.chip_1w -> 7
                R.id.chip_1m -> 30
                R.id.chip_6m -> 180
                R.id.chip_1y -> 365
                else -> 1
            }
            if (currentDays == 1) {
                loadOneDayChart(force = false)
            } else {
                filterAndDisplayChart()
            }
        }

        swipeRefresh.setOnRefreshListener {
            loadData(forceRefresh = true)
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot()
        }

        setupChart()
        loadData(forceRefresh = false)
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


    private fun loadData(forceRefresh: Boolean = false) {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                supervisorScope {
                    val priceDeferred = async { DataRepository.getBitcoinPrice(this@MainActivity, currentCurrency, forceRefresh) }
                    val chartDeferred = async { DataRepository.getMarketChart(this@MainActivity, currentCurrency, "365", forceRefresh) }
                    val oneDayChartDeferred = if (currentDays == 1) {
                        async { DataRepository.getMarketChart(this@MainActivity, currentCurrency, "1", forceRefresh) }
                    } else null
                    val fngDeferred = async { DataRepository.getFearAndGreed(this@MainActivity, forceRefresh) }

                    try {
                        val priceResponse = priceDeferred.await()
                        priceResponse?.let {
                            val price = it.getPrice(currentCurrency)
                            val change = it.get24hChange(currentCurrency)
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                            val formattedPrice = String.format(Locale.US, "%,.2f", price)
                            val formattedChange = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                            val changeColor = if (change >= 0) {
                                ContextCompat.getColor(this@MainActivity, R.color.price_up)
                            } else {
                                ContextCompat.getColor(this@MainActivity, R.color.price_down)
                            }
                            
                            val updateTime = if (it.lastFetchTime > 0) {
                                timeFormat.format(Date(it.lastFetchTime))
                            } else {
                                getString(R.string.price_placeholder)
                            }

                            tvPairLabel.text = getString(R.string.widget_pair_format, currentCurrency.uppercase())
                            tvPrice.text = formattedPrice
                            tvChangePercent.text = formattedChange
                            tvChangePercent.setTextColor(changeColor)
                            tvLastUpdate.text = updateTime
                        } ?: run {
                            tvPrice.text = getString(R.string.price_placeholder)
                            tvChangePercent.text = getString(R.string.change_placeholder)
                            tvLastUpdate.text = getString(R.string.time_placeholder)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, getString(R.string.context_price))
                    }

                    try {
                        val chartResponse = chartDeferred.await()
                        fullYearChartData = chartResponse?.prices
                        
                        val oneDayResponse = oneDayChartDeferred?.await()
                        if (oneDayResponse != null) {
                            oneDayChartData = oneDayResponse.prices
                        }
                        
                        if (currentDays == 1 && oneDayChartData != null) {
                            updateChart(oneDayChartData!!)
                        } else {
                            filterAndDisplayChart()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, getString(R.string.context_chart))
                    }

                    try {
                        val fngResponse = fngDeferred.await()
                        fngResponse?.data?.firstOrNull()?.let {
                            fngGauge.setData(it.value.toIntOrNull() ?: 0, it.valueClassification)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, getString(R.string.context_fng))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.error_unexpected), Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadOneDayChart(force: Boolean) {
        if (!force && oneDayChartData != null) {
            updateChart(oneDayChartData!!)
            return
        }

        lifecycleScope.launch {
            try {
                val response = DataRepository.getMarketChart(this@MainActivity, currentCurrency, "1", forceRefresh = force)
                oneDayChartData = response?.prices
                if (currentDays == 1) {
                    oneDayChartData?.let { updateChart(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun filterAndDisplayChart() {
        val allPrices = fullYearChartData ?: return
        if (allPrices.isEmpty()) return

        val now = System.currentTimeMillis()
        val startTime = now - (currentDays.toLong() * 24 * 60 * 60 * 1000)

        val filteredPrices = if (currentDays >= 365) {
            allPrices
        } else {
            allPrices.filter { it[0] >= startTime }
        }

        updateChart(filteredPrices)
    }

    private fun handleError(e: Exception, context: String) {
        val message = when {
            e is HttpException && e.code() == 429 -> getString(R.string.error_rate_limit)
            e is HttpException -> getString(R.string.error_network, e.code(), context)
            e is UnknownHostException -> getString(R.string.error_no_internet)
            else -> getString(R.string.error_failed_to_load, context)
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        
        if (context == getString(R.string.context_price)) {
            tvPrice.text = when {
                e is HttpException && e.code() == 429 -> getString(R.string.error_429_short)
                e is UnknownHostException -> getString(R.string.error_no_connection_short)
                else -> getString(R.string.error_text)
            }
        }
    }

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

        // Calculate and update change percentage based on selected timeframe
        if (prices.size >= 2) {
            val firstPrice = prices.first()[1]
            val lastPrice = prices.last()[1]
            
            if (firstPrice != 0.0) {
                val change = ((lastPrice - firstPrice) / firstPrice) * 100
                val formattedChange = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                val changeColor = if (change >= 0) {
                    ContextCompat.getColor(this, R.color.price_up)
                } else {
                    ContextCompat.getColor(this, R.color.price_down)
                }

                tvChangePercent.text = formattedChange
                tvChangePercent.setTextColor(changeColor)
            }
        }
        
        // Update X-axis labels
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            private val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            private val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                if (index >= 0 && index < prices.size) {
                    val timestamp = prices[index][0].toLong()
                    return if (currentDays == 1) {
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
    }

    private fun saveChartScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        val container = chart.parent as View
        val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        container.draw(canvas)

        lifecycleScope.launch(Dispatchers.IO) {
            val fileName = getString(R.string.screenshot_filename_prefix) + "${System.currentTimeMillis()}.png"
            val folderName = getString(R.string.screenshot_folder_name)
            var outputStream: OutputStream?

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folderName")
                    }
                    val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = imageUri?.let { contentResolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/$folderName"
                    val dir = File(imagesDir)
                    if (!dir.exists()) dir.mkdirs()
                    val image = File(dir, fileName)
                    outputStream = FileOutputStream(image)
                    
                    // Scan the file so it appears in the gallery
                    MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(image.absolutePath),
                        arrayOf("image/png"),
                        null
                    )
                }

                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.chart_saved), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.chart_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            
            tvDate.text = if (currentDays == 1) {
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
}
