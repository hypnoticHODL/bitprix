package io.github.hypnoticHODL.bitprix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hypnoticHODL.bitprix.data.DataRepository
import io.github.hypnoticHODL.bitprix.model.BitcoinPriceResponse
import io.github.hypnoticHODL.bitprix.model.FearAndGreedResponse
import io.github.hypnoticHODL.bitprix.model.MarketChartResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class MainUiState(
    val isLoading: Boolean = false,
    val currency: String = "usd",
    val timeframe: Int = 1,
    val priceResponse: BitcoinPriceResponse? = null,
    val rawFullYearChartData: List<List<Double>>? = null,
    val rawOneDayChartData: List<List<Double>>? = null,
    val displayChartData: List<List<Double>>? = null,
    val fngResponse: FearAndGreedResponse? = null,
    val error: String? = null,
    val errorContext: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setCurrency(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun setTimeframe(days: Int) {
        _uiState.update { it.copy(timeframe = days) }
        updateDisplayChart()
    }

    private fun updateDisplayChart() {
        val state = _uiState.value
        val data = if (state.timeframe == 1) {
            state.rawOneDayChartData
        } else {
            state.rawFullYearChartData?.let { allPrices ->
                val now = System.currentTimeMillis()
                val startTime = now - (state.timeframe.toLong() * 24 * 60 * 60 * 1000)
                if (state.timeframe >= 365) {
                    allPrices
                } else {
                    allPrices.filter { it[0] >= startTime }
                }
            }
        }
        _uiState.update { it.copy(displayChartData = data) }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val currentCurrency = _uiState.value.currency
            
            try {
                supervisorScope {
                    val priceDeferred = async { DataRepository.getBitcoinPrice(getApplication(), currentCurrency, forceRefresh) }
                    val chartDeferred = async { DataRepository.getMarketChart(getApplication(), currentCurrency, "365", forceRefresh) }
                    val oneDayChartDeferred = async { DataRepository.getMarketChart(getApplication(), currentCurrency, "1", forceRefresh) }
                    val fngDeferred = async { DataRepository.getFearAndGreed(getApplication(), forceRefresh) }

                    val priceResponse = try { priceDeferred.await() } catch (e: Exception) { 
                        _uiState.update { it.copy(error = e.message, errorContext = "Price") }
                        null 
                    }
                    val chartResponse = try { chartDeferred.await() } catch (e: Exception) { null }
                    val oneDayResponse = try { oneDayChartDeferred.await() } catch (e: Exception) { null }
                    val fngResponse = try { fngDeferred.await() } catch (e: Exception) { null }

                    _uiState.update {
                        it.copy(
                            priceResponse = priceResponse,
                            rawFullYearChartData = chartResponse?.prices,
                            rawOneDayChartData = oneDayResponse?.prices,
                            fngResponse = fngResponse,
                            isLoading = false
                        )
                    }
                    updateDisplayChart()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null, errorContext = null) }
    }
}
