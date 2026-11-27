package com.jyotirmoyb.loan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.ComparisonScenarioEvaluated
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import java.io.File
import java.io.FileOutputStream

internal object ComparisonReportExporter {

    fun writeComparisonPdf(context: Context, file: File, report: LoanComparisonReport, currency: CurrencyCode) {
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
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D5D0C3")
            strokeWidth = 1f
        }
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
        canvas.drawText(context.getString(R.string.export_cmp_pdf_title), margin + 16f, y + 28f, titlePaint)
        val savingsText = if (report.maxSavings > 0.0) {
            context.getString(R.string.export_cmp_best_saves, CurrencyFormatter.format(report.maxSavings, currency))
        } else {
            context.getString(R.string.export_cmp_offers_compared, report.scenarios.size)
        }
        canvas.drawText(savingsText, margin + 16f, y + 48f, subtitlePaint)
        y += 82f

        val scenarios = report.scenarios
        val metricColW = 145f
        val scenarioColW = (contentWidth - metricColW) / scenarios.size.coerceAtLeast(1)

        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 54f, 10f, 10f, cardBg)
        canvas.drawText(context.getString(R.string.export_cmp_metric_header), margin + 10f, y + 32f, labelPaint)
        val cheapestBadge = context.getString(R.string.export_cmp_cheapest_badge)
        scenarios.forEachIndexed { idx, s ->
            val x = margin + metricColW + idx * scenarioColW
            if (s.isCheapest) {
                canvas.drawRoundRect(x + 2f, y + 4f, x + scenarioColW - 4f, y + 50f, 8f, 8f, highlightBg)
            }
            canvas.drawText(s.label.take(18), x + 8f, y + 24f, labelPaint)
            val badge = if (s.isCheapest) cheapestBadge else s.categoryName.take(16)
            canvas.drawText(badge, x + 8f, y + 40f, if (s.isCheapest) greenPaint else footerPaint)
        }
        y += 64f

        val totalCostLabel = context.getString(R.string.export_cmp_row_total_cost_gst)
        val savingsLabel = context.getString(R.string.export_cmp_row_savings)
        val rows: List<Pair<String, (ComparisonScenarioEvaluated) -> String>> = listOf(
            context.getString(R.string.export_cmp_row_loan_amount) to { CurrencyFormatter.format(it.input.principal, currency) },
            context.getString(R.string.export_cmp_row_interest_rate) to { CurrencyFormatter.formatPercent(it.input.annualRate) },
            context.getString(R.string.export_cmp_row_tenure) to {
                context.getString(
                    R.string.export_cmp_tenure_fmt,
                    it.result.actualTenureMonths,
                    it.result.actualTenureMonths / 12,
                    it.result.actualTenureMonths % 12
                )
            },
            context.getString(R.string.export_cmp_row_monthly_emi) to { CurrencyFormatter.format(it.result.monthlyEmi, currency, true) },
            context.getString(R.string.export_cmp_row_total_interest) to { CurrencyFormatter.format(it.result.totalInterest, currency) },
            context.getString(R.string.export_cmp_row_fees_gst) to { CurrencyFormatter.format(it.result.totalFees, currency) },
            totalCostLabel to { CurrencyFormatter.format(it.result.effectiveTotalCost, currency) },
            context.getString(R.string.export_cmp_row_loan_free) to { it.result.loanFreeDateLabel.ifBlank { "-" } },
            savingsLabel to {
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
                val paint = if (s.isCheapest && (metricLabel == totalCostLabel || metricLabel == savingsLabel)) {
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
        canvas.drawText(context.getString(R.string.disclaimer_text), margin, pageHeight - 24f, footerPaint)

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
    }

    fun writeComparisonImage(context: Context, file: File, report: LoanComparisonReport, currency: CurrencyCode) {
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
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#DDD9CC")
            strokeWidth = 2f
        }
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
        canvas.drawText(context.getString(R.string.export_cmp_img_title), pad + 32f, pad + 58f, titlePaint)
        val subtitle = if (report.maxSavings > 0.0) {
            context.getString(R.string.export_cmp_img_saves, CurrencyFormatter.format(report.maxSavings, currency))
        } else {
            context.getString(R.string.export_cmp_img_scenarios, scenarios.size)
        }
        canvas.drawText(subtitle, pad + 32f, pad + 100f, subPaint)

        var y = pad + 156f
        val tableW = width - 2 * pad
        val metricW = 280f
        val colW = (tableW - metricW) / scenarios.size.coerceAtLeast(1)

        canvas.drawRoundRect(pad, y, width - pad, y + colHeaderH, 20f, 20f, cardBg)
        canvas.drawText(context.getString(R.string.export_cmp_metric_header), pad + 20f, y + 62f, labelPaint)
        val cheapestBadge = context.getString(R.string.export_cmp_cheapest_badge)
        scenarios.forEachIndexed { idx, s ->
            val x = pad + metricW + idx * colW
            if (s.isCheapest) {
                canvas.drawRoundRect(x + 6f, y + 10f, x + colW - 8f, y + colHeaderH - 10f, 14f, 14f, cheapestBg)
            }
            canvas.drawText(s.label.take(15), x + 16f, y + 48f, labelPaint)
            val tag = if (s.isCheapest) {
                cheapestBadge
            } else {
                context.getString(
                    R.string.export_rate_pa,
                    CurrencyFormatter.formatPercent(s.input.annualRate)
                )
            }
            canvas.drawText(tag, x + 16f, y + 84f, if (s.isCheapest) greenPaint else footPaint)
        }
        y += colHeaderH + 16f

        val totalCostShortLabel = context.getString(R.string.export_cmp_row_total_cost)
        val rows: List<Pair<String, (ComparisonScenarioEvaluated) -> String>> = listOf(
            context.getString(R.string.export_cmp_row_loan_amount) to { CurrencyFormatter.formatCompact(it.input.principal, currency) },
            context.getString(R.string.export_cmp_row_interest_rate) to { CurrencyFormatter.formatPercent(it.input.annualRate) },
            context.getString(R.string.export_cmp_row_monthly_emi) to { CurrencyFormatter.format(it.result.monthlyEmi, currency) },
            context.getString(R.string.export_cmp_row_total_interest) to
                { CurrencyFormatter.formatCompact(it.result.totalInterest, currency) },
            context.getString(R.string.export_cmp_row_fees_gst) to { CurrencyFormatter.format(it.result.totalFees, currency) },
            totalCostShortLabel to { CurrencyFormatter.formatCompact(it.result.effectiveTotalCost, currency) },
            context.getString(R.string.export_cmp_row_tenure) to
                { context.getString(R.string.export_cmp_mos_short, it.result.actualTenureMonths) },
            context.getString(R.string.export_cmp_row_loan_free) to { it.result.loanFreeDateLabel.ifBlank { "-" } }
        )

        canvas.drawRoundRect(pad, y, width - pad, y + rows.size * rowH + 16f, 20f, 20f, cardBg)
        y += 8f
        rows.forEachIndexed { rIdx, (label, fn) ->
            val rowY = y + rIdx * rowH
            canvas.drawText(label, pad + 20f, rowY + 44f, labelPaint)
            scenarios.forEachIndexed { idx, s ->
                val x = pad + metricW + idx * colW
                val p = if (s.isCheapest && label == totalCostShortLabel) greenPaint else valPaint
                canvas.drawText(fn(s).take(16), x + 16f, rowY + 44f, p)
            }
            if (rIdx < rows.lastIndex) {
                canvas.drawLine(pad + 16f, rowY + rowH - 1f, width - pad - 16f, rowY + rowH - 1f, dividerPaint)
            }
        }

        val disclaimer = context.getString(R.string.disclaimer_text)
        val footY = height - 60f
        canvas.drawText(disclaimer.take(86), pad, footY, footPaint)
        if (disclaimer.length > 86) {
            canvas.drawText(disclaimer.substring(86).trim(), pad, footY + 26f, footPaint)
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
