package com.example.bitcoinwidget.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
    private const val FEAR_AND_GREED_BASE_URL = "https://api.alternative.me/"

    val coinGeckoService: CoinGeckoService by lazy {
        Retrofit.Builder()
            .baseUrl(COINGECKO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoService::class.java)
    }

    val fearAndGreedService: FearAndGreedService by lazy {
        Retrofit.Builder()
            .baseUrl(FEAR_AND_GREED_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FearAndGreedService::class.java)
    }
}
