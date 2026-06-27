package io.github.hypnoticHODL.bitprix.network

import io.github.hypnoticHODL.bitprix.model.FearAndGreedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FearAndGreedService {
    @GET("fng/")
    suspend fun getFearAndGreed(
        @Query("limit") limit: Int = 1
    ): FearAndGreedResponse
}
