package com.jyotirmoyb.loan.domain

import java.io.BufferedReader

data class ParsedAmfiNav(
    val schemeCode: String,
    val isinGrowth: String,
    val isinReinvestment: String,
    val schemeName: String,
    val amcName: String,
    val categoryName: String,
    val nav: Double,
    val navDate: String
)

data class AmfiParseSummary(
    val totalParsed: Int,
    val skippedRows: Int,
    val latestNavDate: String,
    val batchesEmitted: Int
)

/**
 * Memory-efficient line-by-line streaming parser for AMFI India's public NAVAll.txt file.
 *
 * Official AMFI semicolon-delimited format:
 *   Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date
 *
 * Non-semicolon lines denote Scheme Category headers (e.g. "Open Ended Schemes(Equity Scheme - Large Cap Fund)")
 * and Fund House / AMC headers (e.g. "HDFC Mutual Fund").
 *
 * Never loads the full multi-megabyte file into memory; emits batches of [batchSize] rows to [onBatch].
 */
object AmfiNavParser {

    fun parseStream(
        reader: BufferedReader,
        batchSize: Int = 500,
        onBatch: (List<ParsedAmfiNav>) -> Unit
    ): AmfiParseSummary {
        val safeBatchSize = batchSize.coerceIn(1, 5000)
        val buffer = ArrayList<ParsedAmfiNav>(safeBatchSize)
        var currentCategory = "Mutual Fund"
        var currentAmc = ""
        var totalParsed = 0
        var skippedRows = 0
        var latestNavDate = ""
        var batchesEmitted = 0

        reader.useLines { sequence ->
            for (rawLine in sequence) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (!line.contains(';')) {
                    // Header line: either Scheme Category or AMC Name
                    if (line.contains("Schemes", ignoreCase = true) ||
                        line.startsWith("Open Ended", ignoreCase = true) ||
                        line.startsWith("Close Ended", ignoreCase = true) ||
                        line.startsWith("Interval Fund", ignoreCase = true)
                    ) {
                        currentCategory = line
                    } else {
                        currentAmc = line
                    }
                    continue
                }

                // Skip column header line
                if (line.startsWith("Scheme Code;", ignoreCase = true)) continue

                val parts = line.split(';')
                if (parts.size < 6) {
                    skippedRows++
                    continue
                }

                val schemeCode = parts[0].trim()
                val isinGrowth = normalizeIsin(parts[1])
                val isinReinv = normalizeIsin(parts[2])
                val schemeName = parts[3].trim()
                val navRaw = parts[4].trim()
                val navDate = parts[5].trim()

                if (schemeCode.isEmpty() || !schemeCode.all { it.isDigit() } || schemeName.isEmpty()) {
                    skippedRows++
                    continue
                }

                val nav = navRaw.toDoubleOrNull()
                if (nav == null || nav <= 0.0 || nav.isNaN() || nav.isInfinite()) {
                    skippedRows++
                    continue
                }

                if (navDate.isNotEmpty() && latestNavDate.isEmpty()) {
                    latestNavDate = navDate
                }

                buffer.add(
                    ParsedAmfiNav(
                        schemeCode = schemeCode,
                        isinGrowth = isinGrowth,
                        isinReinvestment = isinReinv,
                        schemeName = schemeName,
                        amcName = currentAmc,
                        categoryName = currentCategory,
                        nav = nav,
                        navDate = navDate
                    )
                )
                totalParsed++

                if (buffer.size >= safeBatchSize) {
                    onBatch(ArrayList(buffer))
                    buffer.clear()
                    batchesEmitted++
                }
            }
        }

        if (buffer.isNotEmpty()) {
            onBatch(ArrayList(buffer))
            buffer.clear()
            batchesEmitted++
        }

        return AmfiParseSummary(
            totalParsed = totalParsed,
            skippedRows = skippedRows,
            latestNavDate = latestNavDate,
            batchesEmitted = batchesEmitted
        )
    }

    private fun normalizeIsin(raw: String): String {
        val cleaned = raw.trim()
        return if (cleaned == "-" || cleaned.equals("N.A.", ignoreCase = true)) "" else cleaned
    }
}
