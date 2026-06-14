package com.example.bitcoinwidget.model

import com.google.gson.annotations.SerializedName

data class MarketChartResponse(
    @SerializedName("prices") val prices: List<List<Double>>
) {
    @Transient
    var lastFetchTime: Long = 0
}
