package com.example.bitcoinwidget.ui

import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.ChartTouchListener
import android.view.MotionEvent
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.bitcoinwidget.R
import com.example.bitcoinwidget.data.DataRepository
import com.example.bitcoinwidget.widget.WidgetSettingsManager
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
            loadData()
        }

        btnScreenshot.setOnClickListener {
            saveChartScreenshot()
        }

        setupChart()
        loadData()
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
            }

            override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                swipeRefresh.isEnabled = true
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
        xAxis.textColor = Color.GRAY
        xAxis.setLabelCount(4, false)
        xAxis.granularity = 1f

        val yAxis = chart.axisLeft
        yAxis.setDrawGridLines(false)
        yAxis.textColor = Color.GRAY
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
        chart.setExtraOffsets(10f, 10f, 0f, 10f)
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                supervisorScope {
                    val priceDeferred = async { DataRepository.getBitcoinPrice(this@MainActivity, currentCurrency) }
                    val chartDeferred = async { DataRepository.getMarketChart(this@MainActivity, currentCurrency, "365") }
                    val oneDayChartDeferred = if (currentDays == 1) {
                        async { DataRepository.getMarketChart(this@MainActivity, currentCurrency, "1") }
                    } else null
                    val fngDeferred = async { DataRepository.getFearAndGreed(this@MainActivity) }

                    try {
                        val priceResponse = priceDeferred.await()
                        priceResponse?.let {
                            val price = it.getPrice(currentCurrency)
                            val change = it.get24hChange(currentCurrency)
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                            val formattedPrice = String.format(Locale.US, "%,.2f", price)
                            val formattedChange = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                            val changeColor = if (change >= 0) Color.parseColor("#00C853") else Color.parseColor("#FF1744")
                            
                            val updateTime = if (it.lastFetchTime > 0) {
                                timeFormat.format(Date(it.lastFetchTime))
                            } else {
                                "---"
                            }

                            tvPairLabel.text = "Bitcoin ${currentCurrency.uppercase()}"
                            tvPrice.text = formattedPrice
                            tvChangePercent.text = formattedChange
                            tvChangePercent.setTextColor(changeColor)
                            tvLastUpdate.text = updateTime
                        } ?: run {
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            tvPrice.text = "---"
                            tvChangePercent.text = "---"
                            tvLastUpdate.text = timeFormat.format(Date())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, "Price")
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
                        handleError(e, "Chart")
                    }

                    try {
                        val fngResponse = fngDeferred.await()
                        fngResponse?.data?.firstOrNull()?.let {
                            fngGauge.setData(it.value.toIntOrNull() ?: 0, it.valueClassification)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, "Fear & Greed")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
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
                val response = DataRepository.getMarketChart(this@MainActivity, currentCurrency, "1")
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
        val message = when (e) {
            is HttpException -> {
                if (e.code() == 429) "Rate limit reached (429). Please wait."
                else "Network error ${e.code()} in $context"
            }
            else -> "Failed to load $context"
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateChart(prices: List<List<Double>>) {
        if (prices.isEmpty()) return
        
        val entries = prices.mapIndexed { index, list ->
            Entry(index.toFloat(), list[1].toFloat(), list[0].toLong())
        }

        val dataSet = LineDataSet(entries, "BTC Price (${currentCurrency.uppercase()})")
        dataSet.color = Color.parseColor("#F59E0B") // Bitcoin Orange
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2.5f
        dataSet.setDrawFilled(true)
        
        // Use a semi-transparent orange for the area under the curve
        dataSet.fillColor = Color.parseColor("#F59E0B")
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
                val changeColor = if (change >= 0) Color.parseColor("#00C853") else Color.parseColor("#FF1744")

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
        val container = chart.parent as View
        val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        container.draw(canvas)

        lifecycleScope.launch(Dispatchers.IO) {
            val fileName = "Bitprix_Chart_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Bitprix")
                    }
                    val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = imageUri?.let { contentResolver.openOutputStream(it) }
                } else {
                    // Fallback for older versions if needed, but modern apps target Q+
                    // For simplicity, we'll focus on the modern Scoped Storage approach
                }

                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Chart saved!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save chart", Toast.LENGTH_SHORT).show()
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
