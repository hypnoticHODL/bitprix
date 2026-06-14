package com.example.bitcoinwidget.data

import android.content.Context
import android.util.Log
import com.example.bitcoinwidget.model.BitcoinPriceResponse
import com.example.bitcoinwidget.model.MarketChartResponse
import com.example.bitcoinwidget.model.FearAndGreedResponse
import com.example.bitcoinwidget.network.NetworkClient
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataRepository {
    private const val PREFS_NAME = "bitcoin_widget_cache"
    private const val KEY_PRICE = "cached_price"
    private const val KEY_CHART = "cached_chart"
    private const val KEY_FNG = "cached_fng"
    private const val KEY_CURRENCIES = "cached_currencies"
    private const val KEY_LAST_UPDATE = "last_update_time"
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    private val gson = Gson()
    private val fetchMutex = Mutex()
    
    // Memory cache to handle simultaneous requests in the same process
    private val memoryCache = mutableMapOf<String, Pair<Any, Long>>()

    private fun isCacheFresh(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_DURATION
    }

    suspend fun getSupportedCurrencies(context: Context): List<String>? = fetchMutex.withLock {
        val cacheKey = KEY_CURRENCIES
        val timestampKey = "${cacheKey}_timestamp"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check Memory Cache
        memoryCache[cacheKey]?.let { (data, time) ->
            if (isCacheFresh(time)) {
                Log.d("DataRepository", "Returning memory-cached currencies")
                @Suppress("UNCHECKED_CAST")
                return@withLock data as List<String>
            }
        }

        // 2. Check SharedPreferences Cache
        val lastUpdate = prefs.getLong(timestampKey, 0L)
        if (isCacheFresh(lastUpdate)) {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val cached: List<String>? = getFromCache(context, cacheKey, type)
            if (cached != null) {
                memoryCache[cacheKey] = Pair(cached, lastUpdate)
                Log.d("DataRepository", "Returning disk-cached currencies")
                return@withLock cached
            }
        }

        // 3. Network Fetch
        return try {
            val response = NetworkClient.coinGeckoService.getSupportedCurrencies()
            val now = System.currentTimeMillis()
            saveToCache(context, cacheKey, response)
            prefs.edit().putLong(timestampKey, now).apply()
            memoryCache[cacheKey] = Pair(response, now)
            response
        } catch (e: Exception) {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            getFromCache(context, cacheKey, type)
        }
    }

    suspend fun getBitcoinPrice(context: Context, currency: String = "usd"): BitcoinPriceResponse? = fetchMutex.withLock {
        val currencyKey = currency.lowercase()
        val cacheKey = "${KEY_PRICE}_$currencyKey"
        val timestampKey = "${cacheKey}_timestamp"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check Memory Cache
        memoryCache[cacheKey]?.let { (data, time) ->
            if (isCacheFresh(time)) {
                Log.d("DataRepository", "Returning memory-cached price for $currency")
                @Suppress("UNCHECKED_CAST")
                val response = data as BitcoinPriceResponse
                // Ensure the response object itself carries the original fetch time
                response.lastFetchTime = time
                return@withLock response
            }
        }

        // 2. Check SharedPreferences Cache
        val lastUpdate = prefs.getLong(timestampKey, 0L)
        if (lastUpdate > 0 && isCacheFresh(lastUpdate)) {
            val cached = getFromCache(context, cacheKey, BitcoinPriceResponse::class.java)
            if (cached != null) {
                cached.lastFetchTime = lastUpdate
                memoryCache[cacheKey] = Pair(cached, lastUpdate)
                Log.d("DataRepository", "Returning disk-cached price for $currency")
                return@withLock cached
            }
        }

        // 3. Network Fetch
        return try {
            val response = NetworkClient.coinGeckoService.getBitcoinPrice(vsCurrencies = currencyKey)
            val now = System.currentTimeMillis()
            response.lastFetchTime = now
            saveToCache(context, cacheKey, response)
            prefs.edit().putLong(timestampKey, now).apply()
            saveLastUpdateTime(context, now) // Pass the actual fetch time
            memoryCache[cacheKey] = Pair(response, now)
            Log.d("DataRepository", "Successfully fetched price for $currency from network")
            response
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 429) {
                Log.w("DataRepository", "Rate limit (429) hit for $currency. Falling back to stale cache.")
            } else {
                Log.e("DataRepository", "Error fetching price for $currency: ${e.message}")
            }
            val stale = getFromCache(context, cacheKey, BitcoinPriceResponse::class.java)
            val staleTime = prefs.getLong(timestampKey, 0L)
            stale?.lastFetchTime = staleTime
            stale
        }
    }

    suspend fun getMarketChart(context: Context, currency: String = "usd"): MarketChartResponse? = fetchMutex.withLock {
        val currencyKey = currency.lowercase()
        val cacheKey = "${KEY_CHART}_$currencyKey"
        val timestampKey = "${cacheKey}_timestamp"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        memoryCache[cacheKey]?.let { (data, time) ->
            if (isCacheFresh(time)) {
                @Suppress("UNCHECKED_CAST")
                val response = data as MarketChartResponse
                response.lastFetchTime = time
                return@withLock response
            }
        }

        val lastUpdate = prefs.getLong(timestampKey, 0L)
        if (isCacheFresh(lastUpdate)) {
            val cached = getFromCache(context, cacheKey, MarketChartResponse::class.java)
            if (cached != null) {
                cached.lastFetchTime = lastUpdate
                memoryCache[cacheKey] = Pair(cached, lastUpdate)
                return@withLock cached
            }
        }

        return try {
            val response = NetworkClient.coinGeckoService.getMarketChart(vsCurrency = currencyKey)
            val now = System.currentTimeMillis()
            response.lastFetchTime = now
            saveToCache(context, cacheKey, response)
            prefs.edit().putLong(timestampKey, now).apply()
            memoryCache[cacheKey] = Pair(response, now)
            response
        } catch (e: Exception) {
            val stale = getFromCache(context, cacheKey, MarketChartResponse::class.java)
            stale?.lastFetchTime = prefs.getLong(timestampKey, 0L)
            stale
        }
    }

    suspend fun getFearAndGreed(context: Context): FearAndGreedResponse? = fetchMutex.withLock {
        val cacheKey = KEY_FNG
        val timestampKey = "${cacheKey}_timestamp"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        memoryCache[cacheKey]?.let { (data, time) ->
            if (isCacheFresh(time)) {
                @Suppress("UNCHECKED_CAST")
                val response = data as FearAndGreedResponse
                response.lastFetchTime = time
                return@withLock response
            }
        }

        val lastUpdate = prefs.getLong(timestampKey, 0L)
        if (isCacheFresh(lastUpdate)) {
            val cached = getFromCache(context, cacheKey, FearAndGreedResponse::class.java)
            if (cached != null) {
                cached.lastFetchTime = lastUpdate
                memoryCache[cacheKey] = Pair(cached, lastUpdate)
                return@withLock cached
            }
        }

        return try {
            val response = NetworkClient.fearAndGreedService.getFearAndGreed()
            val now = System.currentTimeMillis()
            response.lastFetchTime = now
            saveToCache(context, cacheKey, response)
            prefs.edit().putLong(timestampKey, now).apply()
            memoryCache[cacheKey] = Pair(response, now)
            response
        } catch (e: Exception) {
            val stale = getFromCache(context, cacheKey, FearAndGreedResponse::class.java)
            stale?.lastFetchTime = prefs.getLong(timestampKey, 0L)
            stale
        }
    }

    fun getLastUpdateTime(context: Context): String {
        val timestamp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE, 0L)
        if (timestamp == 0L) return "Never"
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun saveLastUpdateTime(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_UPDATE, timestamp)
            .apply()
    }

    private fun <T> saveToCache(context: Context, key: String, data: T) {
        val json = gson.toJson(data)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, json)
            .apply()
    }

    private fun <T> getFromCache(context: Context, key: String, type: java.lang.reflect.Type): T? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> getFromCache(context: Context, key: String, clazz: Class<T>): T? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            null
        }
    }
}
