package io.github.hypnoticHODL.bitprix.network

import io.github.hypnoticHODL.bitprix.model.BitcoinPriceResponse
import io.github.hypnoticHODL.bitprix.model.MarketChartResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoService {
    @GET("simple/price")
    suspend fun getBitcoinPrice(
        @Query("ids") ids: String = "bitcoin",
        @Query("vs_currencies") vsCurrencies: String,
        @Query("include_24hr_change") includeChange: Boolean = true,
        @Query("precision") precision: String = "2"
    ): BitcoinPriceResponse

    @GET("coins/bitcoin/market_chart")
    suspend fun getMarketChart(
        @Query("vs_currency") vsCurrency: String,
        @Query("days") days: String = "7",
        @Query("interval") interval: String = "daily"
    ): MarketChartResponse

    @GET("simple/supported_vs_currencies")
    suspend fun getSupportedCurrencies(): List<String>
}
