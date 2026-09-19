package io.github.hypnoticHODL.bitprix.data

/**
 * Single source of truth for the fiat currencies exposed to the user.
 */
object CurrencyCatalog {

    val FIAT: Set<String> = setOf(
        "aed", "ars", "aud", "bdt", "bhd", "bmd", "brl", "cad", "chf", "clp",
        "cny", "czk", "dkk", "eur", "gbp", "gel", "hkd", "huf", "idr", "ils",
        "inr", "jpy", "krw", "kwd", "lkr", "mmk", "mxn", "myr", "ngn", "nok",
        "nzd", "php", "pkr", "pln", "rub", "sar", "sek", "sgd", "thb", "try",
        "twd", "uah", "vef", "vnd", "zar", "xdr", "usd"
    )

    val FALLBACK: List<String> = listOf("EUR", "GBP", "USD")

    /**
     * Filters a raw CoinGecko supported-currency list down to the user-facing
     * fiat allowlist, uppercased and sorted alphabetically.
     */
    fun filterSupported(supported: List<String>?): List<String> {
        if (supported.isNullOrEmpty()) return FALLBACK
        val filtered = supported
            .filter { it.lowercase() in FIAT }
            .map { it.uppercase() }
            .sorted()
        return if (filtered.isEmpty()) FALLBACK else filtered
    }

    /**
     * Narrows a currency list by a free-text query, matching on the currency code.
     *
     * Codes only, deliberately: the catalogue carries no display names, and matching on
     * localised names like "euro" or "dollar" is not something a 3-letter code list can
     * support. The search field's hint sets that expectation.
     *
     * @return the whole list for a blank query, otherwise the matching subset in input order.
     */
    fun filterByQuery(currencies: List<String>, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return currencies
        return currencies.filter { it.contains(trimmed, ignoreCase = true) }
    }
}
