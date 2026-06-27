package io.github.hypnoticHODL.bitprix.model

import com.google.gson.annotations.SerializedName

data class BitcoinPriceResponse(
    @SerializedName("bitcoin")
    val prices: Map<String, Double> = emptyMap()
) {
    @Transient
    var lastFetchTime: Long = 0

    fun getPrice(currency: String): Double {
        return prices[currency.lowercase()] ?: 0.0
    }

    fun get24hChange(currency: String): Double {
        return prices["${currency.lowercase()}_24h_change"] ?: 0.0
    }
}
