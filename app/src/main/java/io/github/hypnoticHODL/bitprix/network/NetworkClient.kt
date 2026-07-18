package io.github.hypnoticHODL.bitprix.network

import io.github.hypnoticHODL.bitprix.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
    private const val FEAR_AND_GREED_BASE_URL = "https://api.alternative.me/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val coinGeckoService: CoinGeckoService by lazy {
        Retrofit.Builder()
            .baseUrl(COINGECKO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoService::class.java)
    }

    val fearAndGreedService: FearAndGreedService by lazy {
        Retrofit.Builder()
            .baseUrl(FEAR_AND_GREED_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FearAndGreedService::class.java)
    }
}
