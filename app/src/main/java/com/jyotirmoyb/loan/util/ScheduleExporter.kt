package com.jyotirmoyb.loan.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CalculationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import com.jyotirmoyb.loan.domain.LoanTrackerEngine
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary
import java.io.File
import java.io.FileOutputStream

object ScheduleExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportCsvAndShare(context: Context, title: String, result: CalculationResult, currency: CurrencyCode): Result<Unit> = runCatching {
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "loanjb_schedule" }
        val file = File(exportsDir(context), "${safeName}_schedule.csv")
        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.export_csv_title, title))
        sb.appendLine("${context.getString(R.string.export_csv_currency)},${currency.code}")
        sb.appendLine("${context.getString(R.string.export_csv_monthly_emi)},${result.monthlyEmi}")
        sb.appendLine("${context.getString(R.string.export_csv_total_principal)},${result.totalPrincipal}")
        sb.appendLine("${context.getString(R.string.export_csv_total_interest)},${result.totalInterest}")
        sb.appendLine("${context.getString(R.string.export_csv_total_payment)},${result.totalPayment}")
        sb.appendLine("${context.getString(R.string.export_csv_effective_cost)},${result.effectiveTotalCost}")
        sb.appendLine()
        sb.appendLine(context.getString(R.string.export_csv_columns))
        for (row in result.schedule) {
            sb.appendLine(
                "${row.monthNumber},\"${row.monthLabel}\",\"${row.phaseLabel}\",${row.activeRate},${row.openingBalance},${row.emiPaid},${row.principalPaid},${row.interestPaid},${row.extraPrepayment},${row.closingBalance}"
            )
        }
        sb.appendLine()
        sb.appendLine("\"${context.getString(R.string.disclaimer_text)}\"")
        file.writeText(sb.toString())
        shareFile(context, file, "text/csv", context.getString(R.string.export_share_csv_title))
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

        val pageWidth = 595
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
                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 66f, 10f, 10f, headerBg)
                canvas.drawText(context.getString(R.string.export_pdf_header, title), margin + 14f, y + 28f, titlePaint)
                canvas.drawText(variantSummary.take(80), margin + 14f, y + 48f, subTitlePaint)
                y += 80f

                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 74f, 8f, 8f, cardBg)
                val colW = contentWidth / 4f
                val kpis = listOf(
                    context.getString(R.string.export_kpi_monthly_emi) to CurrencyFormatter.format(result.monthlyEmi, currency, true),
                    context.getString(R.string.export_kpi_principal) to CurrencyFormatter.format(result.totalPrincipal, currency, false),
                    context.getString(
                        R.string.export_kpi_total_interest
                    ) to CurrencyFormatter.format(result.totalInterest, currency, false),
                    context.getString(R.string.export_kpi_effective_cost) to
                        CurrencyFormatter.format(result.effectiveTotalCost, currency, false)
                )
                kpis.forEachIndexed { idx, (k, v) ->
                    val x = margin + 10f + idx * colW
                    canvas.drawText(k, x, y + 24f, labelPaint)
                    canvas.drawText(v, x, y + 44f, valuePaint)
                }
                canvas.drawText(
                    context.getString(
                        R.string.export_pdf_meta_row,
                        result.actualTenureMonths,
                        CurrencyFormatter.format(result.totalFees, currency, false),
                        CurrencyFormatter.formatPercent(result.ltvPercent, 1)
                    ),
                    margin + 10f,
                    y + 64f,
                    labelPaint
                )
                y += 88f
            } else {
                canvas.drawText(context.getString(R.string.export_pdf_header_page, title, pageNum), margin, y + 12f, labelPaint)
                y += 22f
            }

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
            val headers = listOf(
                context.getString(R.string.export_col_num),
                context.getString(R.string.export_col_month),
                context.getString(R.string.export_col_phase),
                context.getString(R.string.export_col_emi),
                context.getString(R.string.export_col_principal),
                context.getString(R.string.export_col_interest),
                context.getString(R.string.export_col_balance)
            )
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

            canvas.drawLine(margin, pageHeight - 36f, pageWidth - margin, pageHeight - 36f, accentPaint)
            canvas.drawText(
                context.getString(R.string.export_pdf_footer_page, context.getString(R.string.disclaimer_text), pageNum),
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
        shareFile(context, file, "application/pdf", context.getString(R.string.export_share_pdf_title))
    }

    fun exportBrandedLoanReportPdfAndShare(context: Context, loan: TrackedLoanSummary, currency: CurrencyCode): Result<Unit> = runCatching {
        val safeName = loan.entity.nickname.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "loanjb_loan_report" }
        val file = File(exportsDir(context), "${safeName}_branded_report.pdf")
        val doc = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 34f
        val contentWidth = pageWidth - 2 * margin

        val headerBg = Paint().apply { color = Color.parseColor("#172B3A") }
        val accentPaint = Paint().apply { color = Color.parseColor("#0B8A7B") }
        val savingsBg = Paint().apply { color = Color.parseColor("#E6F6EE") }
        val cardBg = Paint().apply { color = Color.parseColor("#F3F8F7") }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D9E7E4")
            strokeWidth = 0.8f
        }
        val brandPaint = Paint().apply {
            color = Color.parseColor("#5EEAD4")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9.5f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#536976")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.parseColor("#10222E")
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val greenPaint = Paint(valuePaint).apply { color = Color.parseColor("#047857") }
        val tableHeaderPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val rowTextPaint = Paint().apply {
            color = Color.parseColor("#10222E")
            textSize = 8f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        val rowPrincipalPaint = Paint(rowTextPaint).apply { color = Color.parseColor("#0B8A7B") }
        val rowInterestPaint = Paint(rowTextPaint).apply { color = Color.parseColor("#D97706") }
        val rowPaidPaint = Paint(rowTextPaint).apply { color = Color.parseColor("#047857") }
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            isAntiAlias = true
        }

        val generatedOn = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
            .format(java.util.Date())
        val result = loan.result
        val totalRows = result.schedule.size
        val rowsPerFirstPage = 22
        val rowsPerNextPage = 42
        var rowIndex = 0
        var pageNum = 1

        while (rowIndex < totalRows || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin

            if (pageNum == 1) {
                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 84f, 10f, 10f, headerBg)
                canvas.drawText(context.getString(R.string.export_branded_banner), margin + 14f, y + 20f, brandPaint)
                canvas.drawText("${loan.entity.nickname} (${loan.entity.lender})", margin + 14f, y + 42f, titlePaint)
                val nachLabel = if (loan.autoDebitNach) {
                    context.getString(R.string.export_nach_active)
                } else {
                    context.getString(R.string.export_payment_manual)
                }
                canvas.drawText(
                    context.getString(
                        R.string.export_branded_meta,
                        loan.entity.categoryName,
                        loan.emiDayOfMonth,
                        nachLabel,
                        generatedOn
                    ),
                    margin + 14f,
                    y + 62f,
                    subTitlePaint
                )
                canvas.drawText(
                    context.getString(R.string.export_branded_plan, loan.entity.variantSummary.take(84)),
                    margin + 14f,
                    y + 76f,
                    subTitlePaint
                )
                y += 96f

                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 104f, 8f, 8f, cardBg)
                val colW = contentWidth / 4f
                val row1 = listOf(
                    context.getString(R.string.export_kpi_monthly_emi) to CurrencyFormatter.format(result.monthlyEmi, currency, false),
                    context.getString(R.string.export_kpi_outstanding) to
                        CurrencyFormatter.format(loan.outstandingPrincipal, currency, false),
                    context.getString(R.string.export_kpi_total_principal) to
                        CurrencyFormatter.format(result.totalPrincipal, currency, false),
                    context.getString(R.string.export_kpi_total_interest) to CurrencyFormatter.format(result.totalInterest, currency, false)
                )
                row1.forEachIndexed { idx, (k, v) ->
                    val x = margin + 10f + idx * colW
                    canvas.drawText(k, x, y + 20f, labelPaint)
                    canvas.drawText(v, x, y + 37f, valuePaint)
                }
                canvas.drawLine(margin + 10f, y + 50f, pageWidth - margin - 10f, y + 50f, dividerPaint)
                val row2 = listOf(
                    context.getString(R.string.export_kpi_interest_rate) to
                        context.getString(R.string.export_rate_pa, CurrencyFormatter.formatPercent(loan.input.annualRate)),
                    context.getString(R.string.export_kpi_emis_paid) to "${loan.paidMonthNumbers.size} / ${result.schedule.size}",
                    context.getString(R.string.export_kpi_next_due) to loan.nextDueDateLabel.take(14),
                    context.getString(R.string.export_kpi_loan_free) to result.loanFreeDateLabel.ifBlank { "-" }
                )
                row2.forEachIndexed { idx, (k, v) ->
                    val x = margin + 10f + idx * colW
                    canvas.drawText(k, x, y + 68f, labelPaint)
                    canvas.drawText(v, x, y + 85f, valuePaint)
                }
                y += 114f

                canvas.drawRoundRect(margin, y, pageWidth - margin, y + 36f, 8f, 8f, savingsBg)
                val savedSummary = if (loan.interestSaved > 0.0 || loan.monthsSaved > 0) {
                    context.getString(
                        R.string.export_branded_saved_summary,
                        CurrencyFormatter.format(loan.interestSaved, currency, false),
                        CurrencyFormatter.formatShortLabel(loan.interestSaved, currency),
                        loan.monthsSaved
                    )
                } else {
                    context.getString(
                        R.string.export_branded_total_payable,
                        CurrencyFormatter.format(result.totalPayment, currency, false),
                        CurrencyFormatter.formatShortLabel(result.totalPayment, currency)
                    )
                }
                canvas.drawText(savedSummary, margin + 12f, y + 16f, greenPaint.apply { textSize = 9.5f })
                val form35Suffix = if (LoanTrackerEngine.isVehicleLoan(loan.entity.categoryId, loan.entity.categoryName)) {
                    context.getString(R.string.export_checklist_form35_suffix)
                } else {
                    ""
                }
                canvas.drawText(
                    context.getString(
                        R.string.export_checklist_progress,
                        loan.completedChecklist.size,
                        loan.checklistItems.size,
                        form35Suffix
                    ),
                    margin + 12f,
                    y + 30f,
                    labelPaint
                )
                y += 46f
            } else {
                canvas.drawText(
                    context.getString(R.string.export_branded_page_header, loan.entity.nickname, loan.entity.lender, pageNum),
                    margin,
                    y + 12f,
                    labelPaint
                )
                y += 22f
            }

            canvas.drawRect(margin, y, pageWidth - margin, y + 20f, headerBg)
            val cols = floatArrayOf(
                margin + 5f,
                margin + 32f,
                margin + 90f,
                margin + 136f,
                margin + 208f,
                margin + 288f,
                margin + 366f,
                margin + 444f
            )
            val headers = listOf(
                context.getString(R.string.export_col_num),
                context.getString(R.string.export_col_month),
                context.getString(R.string.export_col_status),
                context.getString(R.string.export_col_rate),
                context.getString(R.string.export_col_emi),
                context.getString(R.string.export_col_principal),
                context.getString(R.string.export_col_interest),
                context.getString(R.string.export_col_balance)
            )
            headers.forEachIndexed { i, h ->
                canvas.drawText(h, cols[i], y + 13f, tableHeaderPaint)
            }
            y += 22f

            val paidLabel = context.getString(R.string.export_status_paid)
            val dueLabel = context.getString(R.string.export_status_due)
            val maxRowsThisPage = if (pageNum == 1) rowsPerFirstPage else rowsPerNextPage
            var count = 0
            while (rowIndex < totalRows && count < maxRowsThisPage) {
                val r = result.schedule[rowIndex]
                val isPaid = loan.paidMonthNumbers.contains(r.monthNumber)
                if (count % 2 == 1) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + 15.5f, cardBg)
                }
                val baseline = y + 11f
                canvas.drawText(r.monthNumber.toString(), cols[0], baseline, rowTextPaint)
                canvas.drawText(r.monthLabel, cols[1], baseline, rowTextPaint)
                canvas.drawText(if (isPaid) paidLabel else dueLabel, cols[2], baseline, if (isPaid) rowPaidPaint else rowTextPaint)
                canvas.drawText("${String.format("%.2f", r.activeRate)}%", cols[3], baseline, rowTextPaint)
                canvas.drawText(CurrencyFormatter.format(r.emiPaid, currency, false), cols[4], baseline, rowTextPaint)
                canvas.drawText(CurrencyFormatter.format(r.principalPaid, currency, false), cols[5], baseline, rowPrincipalPaint)
                canvas.drawText(CurrencyFormatter.format(r.interestPaid, currency, false), cols[6], baseline, rowInterestPaint)
                canvas.drawText(CurrencyFormatter.format(r.closingBalance, currency, false), cols[7], baseline, rowTextPaint)
                canvas.drawLine(margin, y + 15.5f, pageWidth - margin, y + 15.5f, dividerPaint)
                y += 15.5f
                rowIndex++
                count++
            }

            canvas.drawLine(margin, pageHeight - 36f, pageWidth - margin, pageHeight - 36f, accentPaint)
            canvas.drawText(
                context.getString(R.string.export_pdf_footer_page, context.getString(R.string.disclaimer_text), pageNum),
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
        shareFile(context, file, "application/pdf", context.getString(R.string.export_share_branded_title))
    }

    fun exportBackupJsonAndShare(context: Context, jsonContent: String, fileName: String = "loanjb_encrypted_backup.json"): Result<Unit> =
        runCatching {
            val file = File(exportsDir(context), fileName)
            file.writeText(jsonContent)
            shareFile(context, file, "application/json", context.getString(R.string.export_share_backup_title))
        }

    fun exportComparisonPdfAndShare(context: Context, report: LoanComparisonReport, currency: CurrencyCode): Result<Unit> = runCatching {
        require(report.scenarios.isNotEmpty()) { context.getString(R.string.export_err_select_offer) }
        val file = File(exportsDir(context), "loanjb_loan_comparison.pdf")
        ComparisonReportExporter.writeComparisonPdf(context, file, report, currency)
        shareFile(context, file, "application/pdf", context.getString(R.string.export_share_comparison_pdf))
    }

    fun exportComparisonImageAndShare(context: Context, report: LoanComparisonReport, currency: CurrencyCode): Result<Unit> = runCatching {
        require(report.scenarios.isNotEmpty()) { context.getString(R.string.export_err_select_offer) }
        val file = File(exportsDir(context), "loanjb_loan_comparison.png")
        ComparisonReportExporter.writeComparisonImage(context, file, report, currency)
        shareFile(context, file, "image/png", context.getString(R.string.export_share_comparison_image))
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
