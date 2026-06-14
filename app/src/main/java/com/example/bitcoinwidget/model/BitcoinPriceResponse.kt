package com.example.bitcoinwidget.model

class BitcoinPriceResponse : HashMap<String, Map<String, Double>>() {
    @Transient
    var lastFetchTime: Long = 0

    fun getPrice(currency: String): Double {
        return this["bitcoin"]?.get(currency.lowercase()) ?: 0.0
    }

    fun get24hChange(currency: String): Double {
        return this["bitcoin"]?.get("${currency.lowercase()}_24h_change") ?: 0.0
    }
}
