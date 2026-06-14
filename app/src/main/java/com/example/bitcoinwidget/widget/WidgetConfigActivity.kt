package com.example.bitcoinwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bitcoinwidget.R
import com.example.bitcoinwidget.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var spinnerCurrency: Spinner
    private lateinit var btnSave: Button
    private lateinit var rgTheme: RadioGroup
    private lateinit var sbOpacity: SeekBar
    private lateinit var sbTextSize: SeekBar

    // Preview elements
    private lateinit var previewContainer: View
    private lateinit var previewBackground: ImageView
    private lateinit var previewPair: TextView
    private lateinit var previewPrice: TextView
    private lateinit var previewTime: TextView
    private lateinit var previewChange: TextView
    private lateinit var previewRefresh: ImageView

    private val fiatAllowlist = setOf(
        "aed", "ars", "aud", "bdt", "bhd", "bmd", "brl", "cad", "chf", "clp",
        "cny", "czk", "dkk", "eur", "gbp", "gel", "hkd", "huf", "idr", "ils",
        "inr", "jpy", "krw", "kwd", "lkr", "mmk", "mxn", "myr", "ngn", "nok",
        "nzd", "php", "pkr", "pln", "rub", "sar", "sek", "sgd", "thb", "try",
        "twd", "uah", "vef", "vnd", "zar", "xdr", "usd"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Bind Views
        spinnerCurrency = findViewById(R.id.spinner_currency)
        btnSave = findViewById(R.id.btn_save_widget)
        rgTheme = findViewById(R.id.rg_theme)
        sbOpacity = findViewById(R.id.sb_bg_opacity)
        sbTextSize = findViewById(R.id.sb_text_size)

        // Bind Preview Views
        // Note: When using <include android:id="@+id/widget_preview" ... />, 
        // the ID widget_preview is applied to the root view of the included layout.
        val previewRoot = findViewById<View>(R.id.widget_preview)
        previewContainer = previewRoot // The root view itself is the container
        previewBackground = previewRoot.findViewById(R.id.widget_background)
        previewPair = previewRoot.findViewById(R.id.widget_pair_label)
        previewPrice = previewRoot.findViewById(R.id.widget_price_text)
        previewTime = previewRoot.findViewById(R.id.widget_time_label)
        previewChange = previewRoot.findViewById(R.id.widget_change_label)
        previewRefresh = previewRoot.findViewById(R.id.widget_refresh_icon)

        // Setup placeholder text for preview
        previewPair.text = "Bitcoin USD"
        previewPrice.text = "67,234.56"
        previewChange.text = "+2.41%"
        previewChange.setTextColor(Color.parseColor("#00C853"))
        previewTime.text = "19:01"

        // Listeners for Live Preview
        spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rgTheme.setOnCheckedChangeListener { _, _ -> updatePreview() }
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        sbTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnSave.isEnabled = false
        btnSave.setOnClickListener {
            saveConfig()
        }

        loadCurrencies()
        updatePreview()
    }

    private fun updatePreview() {
        val selectedCurrency = spinnerCurrency.selectedItem?.toString()?.lowercase() ?: "usd"
        previewPair.text = "Bitcoin ${selectedCurrency.uppercase()}"

        val isLightTheme = rgTheme.checkedRadioButtonId == R.id.rb_theme_light
        val bgColor = if (isLightTheme) Color.WHITE else Color.BLACK
        val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
        val opacity = sbOpacity.progress
        val textSize = sbTextSize.progress

        val colorWithOpacity = Color.argb(
            opacity,
            Color.red(bgColor),
            Color.green(bgColor),
            Color.blue(bgColor)
        )

        previewBackground.setColorFilter(colorWithOpacity)
        previewBackground.imageAlpha = opacity

        previewPair.setTextColor(textColor)
        previewPrice.setTextColor(textColor)
        previewTime.setTextColor(textColor)
        previewRefresh.setColorFilter(textColor)

        previewPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())

        // Fetch real data for preview
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    DataRepository.getBitcoinPrice(this@WidgetConfigActivity, selectedCurrency)
                }

                if (response != null) {
                    val price = response.getPrice(selectedCurrency)
                    val change = response.get24hChange(selectedCurrency)
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val updateTime = if (response.lastFetchTime > 0) {
                        timeFormat.format(Date(response.lastFetchTime))
                    } else {
                        timeFormat.format(Date())
                    }

                    previewPrice.text = String.format(Locale.US, "%,.2f", price)
                    previewChange.text = String.format(Locale.US, "%s%.2f%%", if (change >= 0) "+" else "", change)
                    val changeColor = if (change >= 0) Color.parseColor("#00C853") else Color.parseColor("#FF1744")
                    previewChange.setTextColor(changeColor)
                    previewTime.text = updateTime
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadCurrencies() {
        lifecycleScope.launch {
            try {
                val currencies = withContext(Dispatchers.IO) {
                    DataRepository.getSupportedCurrencies(this@WidgetConfigActivity)
                }
                
                if (currencies != null) {
                    val filteredCurrencies = currencies
                        .filter { it.lowercase() in fiatAllowlist }
                        .map { it.uppercase() }
                        .sorted()
                    
                    val adapter = ArrayAdapter(
                        this@WidgetConfigActivity,
                        android.R.layout.simple_spinner_item,
                        filteredCurrencies
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    
                    spinnerCurrency.adapter = adapter
                    
                    val usdIndex = filteredCurrencies.indexOf("USD")
                    if (usdIndex >= 0) {
                        spinnerCurrency.setSelection(usdIndex)
                    }

                    btnSave.isEnabled = true
                } else {
                    throw Exception("No currencies returned")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@WidgetConfigActivity, "Failed to load currencies", Toast.LENGTH_LONG).show()
                val fallback = listOf("USD", "EUR", "GBP").sorted()
                val adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_spinner_item, fallback)
                spinnerCurrency.adapter = adapter
                btnSave.isEnabled = true
            }
        }
    }

    private fun saveConfig() {
        val isLightTheme = rgTheme.checkedRadioButtonId == R.id.rb_theme_light
        val bgColor = if (isLightTheme) Color.WHITE else Color.BLACK
        val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
        val bgOpacity = sbOpacity.progress
        val textSize = sbTextSize.progress
        val currency = spinnerCurrency.selectedItem.toString().lowercase()

        WidgetSettingsManager.saveWidgetSettings(
            this, appWidgetId, bgColor, bgOpacity, textColor, textSize, currency
        )

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = BitcoinWidgetProvider()
        provider.onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
