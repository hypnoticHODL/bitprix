package io.github.hypnoticHODL.bitprix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypnoticHODL.bitprix.data.DataRepository
import io.github.hypnoticHODL.bitprix.model.BitcoinPriceResponse
import io.github.hypnoticHODL.bitprix.model.FearAndGreedResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException

/** The period a displayed percentage change refers to. */
enum class ChangePeriod(val days: Int) {
    DAY_24H(1),
    WEEK(7),
    MONTH(30),
    SIX_MONTHS(180),
    YEAR(365);

    companion object {
        fun fromDays(days: Int): ChangePeriod = when (days) {
            7 -> WEEK
            30 -> MONTH
            180 -> SIX_MONTHS
            365 -> YEAR
            else -> DAY_24H
        }
    }
}

/**
 * Failure categories we can explain to the user. Kept free of HTTP codes and
 * internal context tokens so nothing leaks into user-facing copy.
 */
enum class ErrorKind { OFFLINE, RATE_LIMIT, SERVER, UNKNOWN }

internal fun classifyError(e: Throwable): ErrorKind = when {
    e is HttpException && e.code() == 429 -> ErrorKind.RATE_LIMIT
    e is HttpException -> ErrorKind.SERVER
    e is UnknownHostException -> ErrorKind.OFFLINE
    // A dead proxy / refused connection / timeout surfaces as a plain IOException,
    // not UnknownHostException, so treat the whole family as offline.
    e is IOException -> ErrorKind.OFFLINE
    else -> ErrorKind.UNKNOWN
}

data class MainUiState(
    val isLoading: Boolean = false,
    val currency: String = "usd",
    val timeframe: Int = 1,
    val priceResponse: BitcoinPriceResponse? = null,
    val rawFullYearChartData: List<List<Double>>? = null,
    val rawOneDayChartData: List<List<Double>>? = null,
    val displayChartData: List<List<Double>>? = null,
    val fngResponse: FearAndGreedResponse? = null,
    /** Null means "unknown" and must render as a placeholder, never as 0.00%. */
    val changePercent: Double? = null,
    /** Always describes the source [changePercent] was actually derived from. */
    val changePeriod: ChangePeriod = ChangePeriod.DAY_24H,
    val chartUnavailable: Boolean = false,
    val errorKind: ErrorKind? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setCurrency(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun setTimeframe(days: Int) {
        _uiState.update { it.copy(timeframe = days) }
        recomputeDerived()
    }

    /**
     * Recomputes everything downstream of the raw responses.
     *
     * The headline percentage is deliberately timeframe-aware, but the period label is
     * derived from the data that actually produced the number — never from the selected
     * chip. If the chart for the selected range is missing (e.g. a cold start where only
     * the price request succeeded) we fall back to the 24h change and report DAY_24H, so
     * the label can never disagree with the value.
     */
    private fun recomputeDerived() {
        val state = _uiState.value

        val chartData = when {
            state.timeframe == 1 -> state.rawOneDayChartData
            else -> state.rawFullYearChartData?.let { allPrices ->
                if (state.timeframe >= 365) {
                    allPrices
                } else {
                    val startTime = System.currentTimeMillis() - state.timeframe.toLong() * DAY_MS
                    allPrices.filter { it.isNotEmpty() && it[0] >= startTime }
                }
            }
        }

        val fromChart = percentFromChart(chartData)
        val fromPrice = state.priceResponse?.get24hChangeOrNull(state.currency)

        val percent: Double?
        val period: ChangePeriod
        when {
            fromChart != null -> {
                percent = fromChart
                period = ChangePeriod.fromDays(state.timeframe)
            }
            fromPrice != null -> {
                // Chart for this range is unusable — be honest about what we are showing.
                percent = fromPrice
                period = ChangePeriod.DAY_24H
            }
            else -> {
                percent = null
                period = ChangePeriod.DAY_24H
            }
        }

        _uiState.update {
            it.copy(
                displayChartData = chartData,
                changePercent = percent,
                changePeriod = period,
                chartUnavailable = chartData.isNullOrEmpty()
            )
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorKind = null) }
            val currentCurrency = _uiState.value.currency

            try {
                supervisorScope {
                    val priceDeferred = async { DataRepository.getBitcoinPrice(getApplication(), currentCurrency, forceRefresh) }
                    val chartDeferred = async { DataRepository.getMarketChart(getApplication(), currentCurrency, "365", forceRefresh) }
                    val oneDayChartDeferred = async { DataRepository.getMarketChart(getApplication(), currentCurrency, "1", forceRefresh) }
                    val fngDeferred = async { DataRepository.getFearAndGreed(getApplication(), forceRefresh) }

                    var errorKind: ErrorKind? = null

                    val priceResponse = try {
                        priceDeferred.await()
                    } catch (e: Exception) {
                        errorKind = classifyError(e)
                        null
                    }
                    val chartResponse = try { chartDeferred.await() } catch (_: Exception) { null }
                    val oneDayResponse = try { oneDayChartDeferred.await() } catch (_: Exception) { null }
                    val fngResponse = try { fngDeferred.await() } catch (_: Exception) { null }

                    _uiState.update {
                        it.copy(
                            priceResponse = priceResponse,
                            rawFullYearChartData = chartResponse?.prices,
                            rawOneDayChartData = oneDayResponse?.prices,
                            fngResponse = fngResponse,
                            isLoading = false,
                            errorKind = errorKind
                        )
                    }
                    recomputeDerived()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorKind = classifyError(e)) }
                recomputeDerived()
            }
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /**
         * Percentage change across a chart series. Returns null when there is not enough
         * data, or when the opening price is zero and the result would be meaningless.
         */
        internal fun percentFromChart(prices: List<List<Double>>?): Double? {
            if (prices == null || prices.size < 2) return null
            val first = prices.first().getOrNull(1) ?: return null
            val last = prices.last().getOrNull(1) ?: return null
            if (first == 0.0) return null
            return (last - first) / first * 100.0
        }
    }
}
