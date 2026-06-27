package io.github.hypnoticHODL.bitprix.model

import com.google.gson.annotations.SerializedName

data class FearAndGreedResponse(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("data")
    val data: List<FearAndGreedData> = emptyList(),
    @SerializedName("metadata")
    val metadata: FearAndGreedMetadata? = null
) {
    @Transient
    var lastFetchTime: Long = 0
}

data class FearAndGreedData(
    @SerializedName("value")
    val value: String = "0",
    @SerializedName("value_classification")
    val valueClassification: String = "",
    @SerializedName("timestamp")
    val timestamp: String = "",
    @SerializedName("time_until_update")
    val timeUntilUpdate: String? = null
)

data class FearAndGreedMetadata(
    @SerializedName("error")
    val error: String? = null
)
