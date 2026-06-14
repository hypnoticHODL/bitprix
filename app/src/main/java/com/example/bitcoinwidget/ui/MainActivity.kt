package com.example.bitcoinwidget.ui

import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.bitcoinwidget.R
import com.example.bitcoinwidget.data.DataRepository
import com.example.bitcoinwidget.network.NetworkClient
import com.example.bitcoinwidget.widget.WidgetSettingsManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvPairLabel: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvChangePercent: TextView
    private lateinit var chart: LineChart
    private lateinit var tvFngValue: TextView
    private lateinit var tvFngClassification: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var currentCurrency: String = "usd"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        tvFngValue = findViewById(R.id.tv_fng_value)
        tvFngClassification = findViewById(R.id.tv_fng_classification)
        tvLastUpdate = findViewById(R.id.tv_last_update)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        swipeRefresh.setOnRefreshListener {
            loadData()
        }

        setupChart()
        loadData()
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDrawGridBackground(false)
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                supervisorScope {
                    val priceDeferred = async { DataRepository.getBitcoinPrice(this@MainActivity, currentCurrency) }
                    val chartDeferred = async { DataRepository.getMarketChart(this@MainActivity, currentCurrency) }
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
                        chartResponse?.let { updateChart(it.prices) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(e, "Chart")
                    }

                    try {
                        val fngResponse = fngDeferred.await()
                        fngResponse?.data?.firstOrNull()?.let {
                            tvFngValue.text = it.value
                            tvFngClassification.text = it.valueClassification
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
        val entries = prices.mapIndexed { index, list ->
            Entry(index.toFloat(), list[1].toFloat())
        }

        val dataSet = LineDataSet(entries, "BTC Price (${currentCurrency.uppercase()})")
        dataSet.color = Color.BLUE
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2f

        chart.data = LineData(dataSet)
        chart.invalidate()
    }
}
