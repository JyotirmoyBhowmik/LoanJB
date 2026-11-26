package com.jyotirmoyb.loan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jyotirmoyb.loan.domain.ComparisonScenarioEvaluated
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import com.jyotirmoyb.loan.domain.STANDARD_PLANNING_DISCLAIMER
import java.io.File
import java.io.FileOutputStream

internal object ComparisonReportExporter {

    fun writeComparisonPdf(
        file: File,
        report: LoanComparisonReport,
        currency: CurrencyCode
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 32f
        val contentWidth = pageWidth - 2 * margin

        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas

        val bgPaint = Paint().apply { color = Color.parseColor("#ECE9DF") }
        val headerBg = Paint().apply { color = Color.parseColor("#1E2A4A") }
        val cardBg = Paint().apply { color = Color.WHITE }
        val highlightBg = Paint().apply { color = Color.parseColor("#E3EFE2") }
        val accentPaint = Paint().apply { color = Color.parseColor("#5B8A5A") }
        val dividerPaint = Paint().apply { color = Color.parseColor("#D5D0C3"); strokeWidth = 1f }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#C7E2C6")
            textSize = 10f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#52606D")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val greenPaint = Paint(valuePaint).apply { color = Color.parseColor("#2E7D32") }
        val footerPaint = Paint().apply {
            color = Color.parseColor("#52606D")
            textSize = 8f
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        var y = margin
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 66f, 12f, 12f, headerBg)
        canvas.drawText("LoanJB — Side-by-Side Loan Comparison", margin + 16f, y + 28f, titlePaint)
        val savingsText = if (report.maxSavings > 0.0) {
            "Best offer saves up to ${CurrencyFormatter.format(report.maxSavings, currency)} in total cost"
        } else {
            "${report.scenarios.size} loan offer(s) compared"
        }
        canvas.drawText(savingsText, margin + 16f, y + 48f, subtitlePaint)
        y += 82f

        val scenarios = report.scenarios
        val metricColW = 145f
        val scenarioColW = (contentWidth - metricColW) / scenarios.size.coerceAtLeast(1)

        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 54f, 10f, 10f, cardBg)
        canvas.drawText("METRIC", margin + 10f, y + 32f, labelPaint)
        scenarios.forEachIndexed { idx, s ->
            val x = margin + metricColW + idx * scenarioColW
            if (s.isCheapest) {
                canvas.drawRoundRect(x + 2f, y + 4f, x + scenarioColW - 4f, y + 50f, 8f, 8f, highlightBg)
            }
            canvas.drawText(s.label.take(18), x + 8f, y + 24f, labelPaint)
            val badge = if (s.isCheapest) "★ CHEAPEST" else s.categoryName.take(16)
            canvas.drawText(badge, x + 8f, y + 40f, if (s.isCheapest) greenPaint else footerPaint)
        }
        y += 64f

        val rows: List<Pair<String, (ComparisonScenarioEvaluated) -> String>> = listOf(
            "Loan Amount" to { CurrencyFormatter.format(it.input.principal, currency) },
            "Interest Rate" to { CurrencyFormatter.formatPercent(it.input.annualRate) },
            "Tenure" to { "${it.result.actualTenureMonths} mos (${it.result.actualTenureMonths / 12}y ${it.result.actualTenureMonths % 12}m)" },
            "Monthly EMI" to { CurrencyFormatter.format(it.result.monthlyEmi, currency, true) },
            "Total Interest" to { CurrencyFormatter.format(it.result.totalInterest, currency) },
            "Fees + GST" to { CurrencyFormatter.format(it.result.totalFees, currency) },
            "Total Cost (incl. GST)" to { CurrencyFormatter.format(it.result.effectiveTotalCost, currency) },
            "Loan-Free Date" to { it.result.loanFreeDateLabel.ifBlank { "-" } },
            "Savings vs Priciest" to {
                if (it.savingsVsMostExpensive > 0.0) CurrencyFormatter.format(it.savingsVsMostExpensive, currency) else "—"
            }
        )

        canvas.drawRoundRect(margin, y, pageWidth - margin, y + rows.size * 34f + 12f, 10f, 10f, cardBg)
        y += 6f
        rows.forEachIndexed { rIdx, (metricLabel, extractor) ->
            val rowTop = y + rIdx * 34f
            canvas.drawText(metricLabel, margin + 10f, rowTop + 21f, labelPaint)
            scenarios.forEachIndexed { idx, s ->
                val x = margin + metricColW + idx * scenarioColW
                val paint = if (s.isCheapest && (metricLabel.contains("Total Cost") || metricLabel.contains("Savings"))) {
                    greenPaint
                } else {
                    valuePaint
                }
                canvas.drawText(extractor(s).take(20), x + 8f, rowTop + 21f, paint)
            }
            if (rIdx < rows.lastIndex) {
                canvas.drawLine(margin + 8f, rowTop + 33f, pageWidth - margin - 8f, rowTop + 33f, dividerPaint)
            }
        }

        canvas.drawLine(margin, pageHeight - 44f, pageWidth - margin, pageHeight - 44f, accentPaint)
        canvas.drawText(STANDARD_PLANNING_DISCLAIMER, margin, pageHeight - 24f, footerPaint)

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
    }

    fun writeComparisonImage(
        file: File,
        report: LoanComparisonReport,
        currency: CurrencyCode
    ) {
        val scenarios = report.scenarios
        val width = 1080
        val rowH = 68
        val headerH = 190
        val colHeaderH = 110
        val footerH = 120
        val metricCount = 8
        val height = headerH + colHeaderH + (metricCount * rowH) + footerH + 80

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply { color = Color.parseColor("#ECE9DF") }
        val heroBg = Paint().apply { color = Color.parseColor("#1E2A4A") }
        val cardBg = Paint().apply { color = Color.WHITE }
        val cheapestBg = Paint().apply { color = Color.parseColor("#E3EFE2") }
        val dividerPaint = Paint().apply { color = Color.parseColor("#DDD9CC"); strokeWidth = 2f }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#C7E2C6")
            textSize = 24f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val greenPaint = Paint(valPaint).apply { color = Color.parseColor("#2E7D32") }
        val footPaint = Paint().apply {
            color = Color.parseColor("#52606D")
            textSize = 19f
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val pad = 40f

        canvas.drawRoundRect(pad, pad, width - pad, pad + 130f, 24f, 24f, heroBg)
        canvas.drawText("LoanJB — Loan Offer Comparison", pad + 32f, pad + 58f, titlePaint)
        val subtitle = if (report.maxSavings > 0.0) {
            "Cheapest offer saves ${CurrencyFormatter.format(report.maxSavings, currency)} total"
        } else {
            "${scenarios.size} scenario(s) • Offline India Loan Planner"
        }
        canvas.drawText(subtitle, pad + 32f, pad + 100f, subPaint)

        var y = pad + 156f
        val tableW = width - 2 * pad
        val metricW = 280f
        val colW = (tableW - metricW) / scenarios.size.coerceAtLeast(1)

        canvas.drawRoundRect(pad, y, width - pad, y + colHeaderH, 20f, 20f, cardBg)
        canvas.drawText("METRIC", pad + 20f, y + 62f, labelPaint)
        scenarios.forEachIndexed { idx, s ->
            val x = pad + metricW + idx * colW
            if (s.isCheapest) {
                canvas.drawRoundRect(x + 6f, y + 10f, x + colW - 8f, y + colHeaderH - 10f, 14f, 14f, cheapestBg)
            }
            canvas.drawText(s.label.take(15), x + 16f, y + 48f, labelPaint)
            val tag = if (s.isCheapest) "★ CHEAPEST" else "${CurrencyFormatter.formatPercent(s.input.annualRate)} p.a."
            canvas.drawText(tag, x + 16f, y + 84f, if (s.isCheapest) greenPaint else footPaint)
        }
        y += colHeaderH + 16f

        val rows: List<Pair<String, (ComparisonScenarioEvaluated) -> String>> = listOf(
            "Loan Amount" to { CurrencyFormatter.formatCompact(it.input.principal, currency) },
            "Interest Rate" to { CurrencyFormatter.formatPercent(it.input.annualRate) },
            "Monthly EMI" to { CurrencyFormatter.format(it.result.monthlyEmi, currency) },
            "Total Interest" to { CurrencyFormatter.formatCompact(it.result.totalInterest, currency) },
            "Fees + GST" to { CurrencyFormatter.format(it.result.totalFees, currency) },
            "Total Cost" to { CurrencyFormatter.formatCompact(it.result.effectiveTotalCost, currency) },
            "Tenure" to { "${it.result.actualTenureMonths} mos" },
            "Loan-Free Date" to { it.result.loanFreeDateLabel.ifBlank { "-" } }
        )

        canvas.drawRoundRect(pad, y, width - pad, y + rows.size * rowH + 16f, 20f, 20f, cardBg)
        y += 8f
        rows.forEachIndexed { rIdx, (label, fn) ->
            val rowY = y + rIdx * rowH
            canvas.drawText(label, pad + 20f, rowY + 44f, labelPaint)
            scenarios.forEachIndexed { idx, s ->
                val x = pad + metricW + idx * colW
                val p = if (s.isCheapest && label == "Total Cost") greenPaint else valPaint
                canvas.drawText(fn(s).take(16), x + 16f, rowY + 44f, p)
            }
            if (rIdx < rows.lastIndex) {
                canvas.drawLine(pad + 16f, rowY + rowH - 1f, width - pad - 16f, rowY + rowH - 1f, dividerPaint)
            }
        }

        val footY = height - 60f
        canvas.drawText(STANDARD_PLANNING_DISCLAIMER.take(86), pad, footY, footPaint)
        if (STANDARD_PLANNING_DISCLAIMER.length > 86) {
            canvas.drawText(STANDARD_PLANNING_DISCLAIMER.substring(86).trim(), pad, footY + 26f, footPaint)
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
