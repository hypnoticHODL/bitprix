package com.example.bitcoinwidget.model

import com.google.gson.annotations.SerializedName

data class FearAndGreedResponse(
    @SerializedName("data") val data: List<FearAndGreedData>
) {
    @Transient
    var lastFetchTime: Long = 0
}

data class FearAndGreedData(
    @SerializedName("value") val value: String,
    @SerializedName("value_classification") val valueClassification: String,
    @SerializedName("timestamp") val timestamp: String
)
