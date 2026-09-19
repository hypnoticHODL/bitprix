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
        return get24hChangeOrNull(currency) ?: 0.0
    }

    /**
     * The 24h change, or null when the API did not supply one.
     *
     * Distinct from [get24hChange] because a missing change is not the same as a change of
     * zero, and the UI must be able to tell "flat" apart from "unknown".
     */
    fun get24hChangeOrNull(currency: String): Double? {
        return prices["${currency.lowercase()}_24h_change"]
    }

    /** True when the API returned no usable price for this currency. */
    fun hasPrice(currency: String): Boolean {
        return prices.containsKey(currency.lowercase())
    }
}
