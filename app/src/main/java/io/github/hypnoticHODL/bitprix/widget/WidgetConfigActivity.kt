package io.github.hypnoticHODL.bitprix.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.hypnoticHODL.bitprix.R
import io.github.hypnoticHODL.bitprix.data.CurrencyCatalog
import io.github.hypnoticHODL.bitprix.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var spinnerCurrency: Spinner
    private lateinit var spinnerRefresh: Spinner
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

    // Currency restored when reconfiguring an existing widget (null on first setup)
    private var savedCurrency: String? = null

    /**
     * The live price snapshot backing the preview.
     *
     * Styling changes (theme / opacity / text size) reuse this instead of refetching, so
     * dragging a slider no longer fires a network request per pixel — which previously
     * produced hundreds of racing CoinGecko calls per drag and risked HTTP 429.
     */
    private data class PriceSnapshot(val price: Double, val change: Double, val timeLabel: String)

    private var snapshot: PriceSnapshot? = null
    private var dataJob: Job? = null

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        val rootView = findViewById<View>(R.id.scroll_view_root)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        spinnerRefresh = findViewById(R.id.spinner_refresh_interval)
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
        previewPair.text = getString(R.string.widget_pair_format, "USD")
        previewPrice.text = getString(R.string.placeholder_price)
        previewChange.text = getString(R.string.placeholder_change)
        previewChange.setTextColor(ContextCompat.getColor(this, R.color.price_up))
        previewTime.text = getString(R.string.placeholder_time)

        // Restore existing configuration when reconfiguring a widget
        restoreSavedSettings()

        // Listeners for Live Preview.
        // Only a currency change can alter the data, so only it refetches. Every other control
        // is a pure restyle of the existing snapshot.
        spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshPreviewForCurrency()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rgTheme.setOnCheckedChangeListener { _, _ -> renderPreview() }
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { renderPreview() }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        sbTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { renderPreview() }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnSave.isEnabled = false
        btnSave.setOnClickListener {
            saveConfig()
        }

        loadCurrencies()
        renderPreview()
        refreshPreviewForCurrency()
    }

    private fun restoreSavedSettings() {
        if (!WidgetSettingsManager.hasSavedSettings(this, appWidgetId)) return

        // Theme is stored explicitly. Inferring it from the background colour meant any colour
        // that was not exactly Color.WHITE silently restored as Dark.
        val isLight = WidgetSettingsManager.getIsLightTheme(this, appWidgetId)
            ?: (WidgetSettingsManager.getBgColor(this, appWidgetId) == Color.WHITE)
        rgTheme.check(if (isLight) R.id.rb_theme_light else R.id.rb_theme_dark)

        sbOpacity.progress = WidgetSettingsManager.getBgOpacity(this, appWidgetId)
            .coerceIn(0, sbOpacity.max)
        sbTextSize.progress = WidgetSettingsManager.getTextSize(this, appWidgetId)
            .coerceIn(sbTextSize.min, sbTextSize.max)

        val refreshValues = resources.getStringArray(R.array.refresh_interval_values)
        val refreshIndex = refreshValues.indexOf(
            WidgetSettingsManager.getRefreshInterval(this, appWidgetId).toString()
        )
        if (refreshIndex >= 0) {
            spinnerRefresh.setSelection(refreshIndex)
        }

        savedCurrency = WidgetSettingsManager.getCurrency(this, appWidgetId)
    }

    /** Re-renders the preview from local state only. Never touches the network. */
    private fun renderPreview() {
        val selectedCurrency = currentPreviewCurrency()
        previewPair.text = getString(R.string.widget_pair_format, selectedCurrency.uppercase(Locale.US))

        val isLightTheme = rgTheme.checkedRadioButtonId == R.id.rb_theme_light
        val bgColor = if (isLightTheme) Color.WHITE else Color.BLACK
        val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
        val opacity = sbOpacity.progress
        val textSize = sbTextSize.progress

        // Tint with an opaque color; transparency is applied once via imageAlpha.
        val tintColor = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))

        previewBackground.setColorFilter(tintColor)
        previewBackground.imageAlpha = opacity

        previewPair.setTextColor(textColor)
        previewPrice.setTextColor(textColor)
        previewTime.setTextColor(textColor)
        previewRefresh.setColorFilter(textColor)

        previewPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())

        val snap = snapshot
        if (snap == null) {
            previewPrice.text = getString(R.string.placeholder_price)
            previewChange.text = getString(R.string.placeholder_change)
            previewTime.text = getString(R.string.placeholder_time)
            previewChange.setTextColor(ContextCompat.getColor(this, R.color.price_up))
            return
        }

        previewPrice.text = String.format(Locale.US, "%,.2f", snap.price)
        // Mirrors the shipped widget, which labels its 24h change so it cannot be mistaken
        // for the timeframe-dependent figure the app shows.
        previewChange.text = getString(
            R.string.widget_change_24h_format,
            String.format(Locale.US, "%s%.2f%%", if (snap.change >= 0) "+" else "", snap.change)
        )
        previewChange.setTextColor(
            ContextCompat.getColor(
                this,
                if (snap.change >= 0) R.color.price_up else R.color.price_down
            )
        )
        previewTime.text = snap.timeLabel
    }

    private fun currentPreviewCurrency(): String =
        spinnerCurrency.selectedItem?.toString()?.lowercase(Locale.US) ?: "usd"

    /**
     * Fetches a fresh price snapshot for the selected currency.
     *
     * Cancels any in-flight fetch and debounces, so rapid spinner changes collapse into a
     * single request rather than a burst of racing ones.
     */
    private fun refreshPreviewForCurrency() {
        renderPreview()

        dataJob?.cancel()
        val currency = currentPreviewCurrency()

        dataJob = lifecycleScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val response = try {
                withContext(Dispatchers.IO) {
                    DataRepository.getBitcoinPrice(this@WidgetConfigActivity, currency)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (!isActive) return@launch
            if (response == null) return@launch

            val updateTime = if (response.lastFetchTime > 0) {
                timeFormat.format(Date(response.lastFetchTime))
            } else {
                timeFormat.format(Date())
            }

            snapshot = PriceSnapshot(
                price = response.getPrice(currency),
                change = response.get24hChange(currency),
                timeLabel = updateTime
            )
            renderPreview()
        }
    }

    private fun loadCurrencies() {
        lifecycleScope.launch {
            val currencies = try {
                withContext(Dispatchers.IO) {
                    DataRepository.getSupportedCurrencies(this@WidgetConfigActivity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (currencies == null) {
                Toast.makeText(
                    this@WidgetConfigActivity,
                    getString(R.string.error_failed_to_load, getString(R.string.context_currencies)),
                    Toast.LENGTH_LONG
                ).show()
            }

            val filteredCurrencies = CurrencyCatalog.filterSupported(currencies)

            val adapter = ArrayAdapter(
                this@WidgetConfigActivity,
                android.R.layout.simple_spinner_item,
                filteredCurrencies
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spinnerCurrency.adapter = adapter

            val preferred = (savedCurrency ?: "usd").uppercase(Locale.US)
            val preferredIndex = filteredCurrencies.indexOf(preferred)
            val selectionIndex = if (preferredIndex >= 0) {
                preferredIndex
            } else {
                filteredCurrencies.indexOf("USD").coerceAtLeast(0)
            }
            spinnerCurrency.setSelection(selectionIndex)

            btnSave.isEnabled = true
        }
    }

    private fun saveConfig() {
        val isLightTheme = rgTheme.checkedRadioButtonId == R.id.rb_theme_light
        val bgColor = if (isLightTheme) Color.WHITE else Color.BLACK
        val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
        val bgOpacity = sbOpacity.progress
        val textSize = sbTextSize.progress
        val currency = spinnerCurrency.selectedItem.toString().lowercase(Locale.US)

        val refreshValues = resources.getStringArray(R.array.refresh_interval_values)
        val refreshInterval = refreshValues[spinnerRefresh.selectedItemPosition].toInt()

        WidgetSettingsManager.saveWidgetSettings(
            this, appWidgetId, bgColor, bgOpacity, textColor, textSize, currency, refreshInterval,
            isLightTheme
        )

        // Trigger update via broadcast
        val updateIntent = Intent(this, BitcoinWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(updateIntent)

        // Reschedule Worker to apply new refresh interval if needed
        BitcoinWidgetWorker.enqueueWork(this)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 300L
    }
}
