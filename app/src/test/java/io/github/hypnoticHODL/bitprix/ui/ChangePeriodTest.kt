package io.github.hypnoticHODL.bitprix.ui

import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The headline percentage is timeframe-aware, so the label describing it must be derived
 * from the data source rather than the selected chip. These tests pin that contract.
 */
class ChangePeriodTest {

    private fun chart(vararg prices: Double): List<List<Double>> =
        prices.mapIndexed { i, p -> listOf((1_700_000_000_000L + i * 3_600_000L).toDouble(), p) }

    @Test
    fun `percent across a simple series`() {
        val result = MainViewModel.percentFromChart(chart(100.0, 110.0))
        assertEquals(10.0, result!!, 0.0001)
    }

    @Test
    fun `negative percent when the series falls`() {
        val result = MainViewModel.percentFromChart(chart(100.0, 75.0))
        assertEquals(-25.0, result!!, 0.0001)
    }

    @Test
    fun `percent is null when there is not enough data`() {
        assertNull(MainViewModel.percentFromChart(null))
        assertNull(MainViewModel.percentFromChart(emptyList()))
        assertNull(MainViewModel.percentFromChart(chart(100.0)))
    }

    @Test
    fun `percent is null rather than infinite when the opening price is zero`() {
        // Previously this produced a divide-by-zero that rendered as "+Infinity%".
        assertNull(MainViewModel.percentFromChart(chart(0.0, 100.0)))
    }

    @Test
    fun `malformed rows do not throw`() {
        // A first row with no price element has no opening value to divide against, so the
        // safe answer is null (render a placeholder) rather than a crash or a bogus number.
        assertNull(MainViewModel.percentFromChart(listOf(listOf(1.0), listOf(1.0, 2.0, 3.0))))

        // A ragged final row is likewise unusable and must not throw.
        assertNull(MainViewModel.percentFromChart(listOf(listOf(1.0, 100.0), listOf(2.0))))

        // Extra columns on the last row are harmless.
        assertEquals(
            100.0,
            MainViewModel.percentFromChart(listOf(listOf(1.0, 100.0), listOf(2.0, 200.0, 999.0)))!!,
            0.0001
        )
    }

    @Test
    fun `days map onto the expected period`() {
        assertEquals(ChangePeriod.DAY_24H, ChangePeriod.fromDays(1))
        assertEquals(ChangePeriod.WEEK, ChangePeriod.fromDays(7))
        assertEquals(ChangePeriod.MONTH, ChangePeriod.fromDays(30))
        assertEquals(ChangePeriod.SIX_MONTHS, ChangePeriod.fromDays(180))
        assertEquals(ChangePeriod.YEAR, ChangePeriod.fromDays(365))
        // Anything unrecognised must fall back to the safest label, not to the raw chip text.
        assertEquals(ChangePeriod.DAY_24H, ChangePeriod.fromDays(999))
    }

    @Test
    fun `rate limit is classified separately from other server errors`() {
        assertEquals(ErrorKind.RATE_LIMIT, classifyError(HttpException(Response.error<Any>(429, ResponseBody.create(null, "")))))
        assertEquals(ErrorKind.SERVER, classifyError(HttpException(Response.error<Any>(500, ResponseBody.create(null, "")))))
    }

    @Test
    fun `dns failure is offline`() {
        assertEquals(ErrorKind.OFFLINE, classifyError(UnknownHostException("no dns")))
    }

    @Test
    fun `connection refused and timeouts are also offline`() {
        // A dead proxy or a dropped connection surfaces as a plain IOException, so the whole
        // family must be treated as "offline" or the user sees a generic failure instead.
        assertEquals(ErrorKind.OFFLINE, classifyError(IOException("connection refused")))
        assertEquals(ErrorKind.OFFLINE, classifyError(SocketTimeoutException("timeout")))
    }

    @Test
    fun `unknown failures stay unknown`() {
        assertEquals(ErrorKind.UNKNOWN, classifyError(IllegalStateException("boom")))
    }
}
