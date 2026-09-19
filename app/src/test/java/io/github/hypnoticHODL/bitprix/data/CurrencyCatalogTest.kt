package io.github.hypnoticHODL.bitprix.data

import io.github.hypnoticHODL.bitprix.model.BitcoinPriceResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyCatalogTest {

    @Test
    fun `filters to the fiat allowlist and sorts`() {
        val result = CurrencyCatalog.filterSupported(listOf("btc", "usd", "eur", "eth", "gbp"))
        assertEquals(listOf("EUR", "GBP", "USD"), result)
    }

    @Test
    fun `falls back when nothing matches`() {
        assertEquals(CurrencyCatalog.FALLBACK, CurrencyCatalog.filterSupported(listOf("btc", "eth")))
    }

    @Test
    fun `falls back when the input is missing`() {
        assertEquals(CurrencyCatalog.FALLBACK, CurrencyCatalog.filterSupported(null))
        assertEquals(CurrencyCatalog.FALLBACK, CurrencyCatalog.filterSupported(emptyList()))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals(listOf("USD"), CurrencyCatalog.filterSupported(listOf("USD")))
    }

    @Test
    fun `query returns the whole list when blank`() {
        val all = CurrencyCatalog.FALLBACK
        assertEquals(all, CurrencyCatalog.filterByQuery(all, ""))
        assertEquals(all, CurrencyCatalog.filterByQuery(all, "   "))
    }

    @Test
    fun `query matches on code, case insensitively`() {
        val all = listOf("EUR", "GBP", "USD")
        assertEquals(listOf("USD"), CurrencyCatalog.filterByQuery(all, "usd"))
        assertEquals(listOf("USD"), CurrencyCatalog.filterByQuery(all, "USD"))
    }

    @Test
    fun `query matches substrings`() {
        val all = listOf("EUR", "GBP", "USD")
        // "u" should reach both EUR and USD, not just the prefix.
        assertEquals(listOf("EUR", "USD"), CurrencyCatalog.filterByQuery(all, "u"))
    }

    @Test
    fun `query trims surrounding whitespace`() {
        val all = listOf("EUR", "GBP", "USD")
        assertEquals(listOf("USD"), CurrencyCatalog.filterByQuery(all, "  USD  "))
    }

    @Test
    fun `query with no match yields empty, not the fallback`() {
        // The picker shows a "no results" view for this; returning the full list would make
        // an unmatched search silently look like a successful one.
        assertEquals(emptyList<String>(), CurrencyCatalog.filterByQuery(listOf("EUR", "USD"), "zzz"))
    }
}

class BitcoinPriceResponseTest {

    private val response = BitcoinPriceResponse(
        prices = mapOf("usd" to 81_290.0, "usd_24h_change" to 4.33, "eur" to 70_000.0)
    )

    @Test
    fun `reads price and change`() {
        assertEquals(81_290.0, response.getPrice("usd"), 0.001)
        assertEquals(4.33, response.get24hChange("usd"), 0.001)
    }

    @Test
    fun `is case insensitive on the currency`() {
        assertEquals(81_290.0, response.getPrice("USD"), 0.001)
    }

    @Test
    fun `missing change is null rather than zero`() {
        // The distinction matters: "no data" must render as a placeholder, not as "0.00%",
        // which a user would read as a real measurement meaning the price is flat.
        assertNull(response.get24hChangeOrNull("eur"))
        assertEquals(0.0, response.get24hChange("eur"), 0.001)
    }

    @Test
    fun `hasPrice distinguishes missing currencies`() {
        assertTrue(response.hasPrice("usd"))
        assertFalse(response.hasPrice("jpy"))
    }

    @Test
    fun `unknown currency yields zero price`() {
        assertEquals(0.0, response.getPrice("jpy"), 0.001)
    }
}
