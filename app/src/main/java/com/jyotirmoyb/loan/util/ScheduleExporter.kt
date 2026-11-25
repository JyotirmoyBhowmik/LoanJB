package com.jyotirmoyb.loan.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream

object ScheduleExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportCsvAndShare(
        context: Context,
        title: String,
        result: CalculationResult,
        currency: CurrencyCode
    ): Result<Unit> = runCatching {
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "loanjb_schedule" }
        val file = File(exportsDir(context), "${safeName}_schedule.csv")
        val sb = StringBuilder()
        sb.appendLine("LoanJB Amortization Schedule - $title")
        sb.appendLine("Currency,${currency.code}")
        sb.appendLine("Monthly EMI,${result.monthlyEmi}")
        sb.appendLine("Total Principal,${result.totalPrincipal}")
        sb.appendLine("Total Interest,${result.totalInterest}")
        sb.appendLine("Total Payment,${result.totalPayment}")
        sb.appendLine("Effective Total Cost (incl. Fees),${result.effectiveTotalCost}")
        sb.appendLine()
        sb.appendLine("Month #,Date,Phase,Rate %,Opening Balance,EMI Paid,Principal Paid,Interest Paid,Extra Prepayment,Closing Balance")
        for (row in result.schedule) {
            sb.appendLine(
                "${row.monthNumber},\"${row.monthLabel}\",\"${row.phaseLabel}\",${row.activeRate},${row.openingBalance},${row.emiPaid},${row.principalPaid},${row.interestPaid},${row.extraPrepayment},${row.closingBalance}"
            )
        }
        sb.appendLine()
        sb.appendLine("\"Estimates only. Actual bank terms, rates and rules (including Nepal Rastra Bank limits) may differ.\"")
        file.writeText(sb.toString())
        shareFile(context, file, "text/csv", "Share Schedule CSV")
    }

    fun exportPdfAndShare(
        context: Context,
        title: String,
        variantSummary: String,
        result: CalculationResult,
        currency: CurrencyCode
    ): Result<Unit> = runCatching {
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "loanjb_report" }
        val file = File(exportsDir(context), "${safeName}_schedule.pdf")
        val doc = PdfDocument()

        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - 2 * margin

        val headerBg = Paint().apply { color = Color.parseColor("#172B3A") }
        val accentPaint = Paint().apply { color = Color.parseColor("#0B8A7B") }
        val cardBg = Paint().apply { color = Color.parseColor("#F3F8F7") }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D9E7E4")
            strokeWidth = 0.8f
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#B8E3DD")
            textSize = 10f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#536976")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.parseColor("#10222E")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val rowTextPaint = Paint().apply {
            color = Color.parseColor("#10222E")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        val rowPrincipalPaint = Paint(rowTextPaint).apply { color = Color.parseColor("#0B8A7B") }
        val rowInterestPaint = Paint(rowTextPaint).apply { color = Color.parseColor("#D97706") }
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            isAntiAlias = true
        }

        val rowsPerFirstPage = 30
        val rowsPerNextPage = 42
        val totalRows = result.schedule.size
        var rowIndex = 0
        var pageNum = 1

        while (rowIndex < totalRows || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin

            if (pageNum == 1) {
                // Header banner
                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 66f, 10f, 10f, headerBg)
                canvas.drawText("LoanJB — $title", margin + 14f, y + 28f, titlePaint)
                canvas.drawText(variantSummary.take(80), margin + 14f, y + 48f, subTitlePaint)
                y += 80f

                // Summary KPI box
                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 74f, 8f, 8f, cardBg)
                val colW = contentWidth / 4f
                val kpis = listOf(
                    "MONTHLY EMI" to CurrencyFormatter.format(result.monthlyEmi, currency, true),
                    "PRINCIPAL" to CurrencyFormatter.format(result.totalPrincipal, currency, false),
                    "TOTAL INTEREST" to CurrencyFormatter.format(result.totalInterest, currency, false),
                    "EFFECTIVE COST" to CurrencyFormatter.format(result.effectiveTotalCost, currency, false)
                )
                kpis.forEachIndexed { idx, (k, v) ->
                    val x = margin + 10f + idx * colW
                    canvas.drawText(k, x, y + 24f, labelPaint)
                    canvas.drawText(v, x, y + 44f, valuePaint)
                }
                canvas.drawText(
                    "Tenure: ${result.actualTenureMonths} months  |  Fees: ${CurrencyFormatter.format(result.totalFees, currency, false)}  |  LTV: ${CurrencyFormatter.formatPercent(result.ltvPercent, 1)}",
                    margin + 10f,
                    y + 64f,
                    labelPaint
                )
                y += 88f
            } else {
                canvas.drawText("LoanJB — $title (Page $pageNum)", margin, y + 12f, labelPaint)
                y += 22f
            }

            // Table header
            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, headerBg)
            val cols = floatArrayOf(
                margin + 6f,
                margin + 42f,
                margin + 108f,
                margin + 188f,
                margin + 286f,
                margin + 382f,
                margin + 462f
            )
            val headers = listOf("#", "Month", "Phase", "EMI", "Principal", "Interest", "Balance")
            headers.forEachIndexed { i, h ->
                canvas.drawText(h, cols[i], y + 14f, tableHeaderPaint)
            }
            y += 24f

            val maxRowsThisPage = if (pageNum == 1) rowsPerFirstPage else rowsPerNextPage
            var count = 0
            while (rowIndex < totalRows && count < maxRowsThisPage) {
                val r = result.schedule[rowIndex]
                if (count % 2 == 1) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + 16f, cardBg)
                }
                val baseline = y + 11.5f
                canvas.drawText(r.monthNumber.toString(), cols[0], baseline, rowTextPaint)
                canvas.drawText(r.monthLabel, cols[1], baseline, rowTextPaint)
                canvas.drawText(r.phaseLabel.take(11), cols[2], baseline, rowTextPaint)
                canvas.drawText(CurrencyFormatter.format(r.emiPaid, currency, false), cols[3], baseline, rowTextPaint)
                canvas.drawText(CurrencyFormatter.format(r.principalPaid, currency, false), cols[4], baseline, rowPrincipalPaint)
                canvas.drawText(CurrencyFormatter.format(r.interestPaid, currency, false), cols[5], baseline, rowInterestPaint)
                canvas.drawText(CurrencyFormatter.format(r.closingBalance, currency, false), cols[6], baseline, rowTextPaint)
                canvas.drawLine(margin, y + 16f, pageWidth - margin, y + 16f, dividerPaint)
                y += 16f
                rowIndex++
                count++
            }

            // Footer disclaimer
            canvas.drawLine(margin, pageHeight - 36f, pageWidth - margin, pageHeight - 36f, accentPaint)
            canvas.drawText(
                "Estimates only. Actual bank terms, rates and rules (including Nepal Rastra Bank limits) may differ. • Page $pageNum",
                margin,
                pageHeight - 20f,
                footerPaint
            )

            doc.finishPage(page)
            pageNum++
            if (totalRows == 0) break
        }

        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        shareFile(context, file, "application/pdf", "Share Schedule PDF")
    }

    fun exportBackupJsonAndShare(context: Context, jsonContent: String): Result<Unit> = runCatching {
        val file = File(exportsDir(context), "loanjb_backup.json")
        file.writeText(jsonContent)
        shareFile(context, file, "application/json", "Export LoanJB Backup JSON")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
