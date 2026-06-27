package io.github.hypnoticHODL.bitprix.model

import com.google.gson.annotations.SerializedName

data class MarketChartResponse(
    @SerializedName("prices")
    val prices: List<List<Double>> = emptyList(),
    @SerializedName("market_caps")
    val marketCaps: List<List<Double>> = emptyList(),
    @SerializedName("total_volumes")
    val totalVolumes: List<List<Double>> = emptyList()
) {
    @Transient
    var lastFetchTime: Long = 0
}
