package com.jyotirmoyb.loan.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.ui.components.BalanceOverTimeLineChart
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.PrincipalInterestDonutChart
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
fun CalculatorResultSection(
    input: CalculationInput,
    category: LoanCategory?,
    result: CalculationResult,
    currency: CurrencyCode,
    onSaveLoanClick: () -> Unit,
    onAddToCompareClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onExportPdfClick: () -> Unit
) {
    if (!result.isValid) {
        FintechCard(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            borderColor = MaterialTheme.colorScheme.error
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = "Validation error", tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Please fix inputs to calculate",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            result.validationErrors.forEach { err ->
                Text("• $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        return
    }

    val flags = input.ruleFlags.toSet()
    val isStep = flags.contains("EMI_STEP_UP") || flags.contains("EMI_STEP_DOWN")
    val isBullet = flags.contains("ASSET_GOLD_BULLET")
    val isBill = flags.contains("BIZ_BILL_DISCOUNT")
    val isLc = flags.contains("BIZ_LC_TR")
    val isFlexi = flags.contains("FAC_FLEXI_LOC") || flags.contains("FAC_OVERDRAFT") || flags.contains("BIZ_CASH_CREDIT")

    val headlineLabel = when {
        isBill -> "ADVANCE AMOUNT (${input.billDays}d)"
        isLc -> "MATURITY SETTLEMENT (${input.lcDays}d)"
        isBullet -> "BULLET PAYMENT AT MATURITY"
        flags.contains("ASSET_GOLD_INTEREST_ONLY") -> "MONTHLY INTEREST-ONLY"
        isFlexi -> "AVG MONTHLY INTEREST (DRAWN)"
        isStep -> "STARTING → ENDING EMI"
        else -> "MONTHLY EMI"
    }

    val headlineValue = when {
        isBullet -> CurrencyFormatter.format(result.lastEmi, currency, true)
        isStep -> "${CurrencyFormatter.format(result.firstEmi, currency)} → ${CurrencyFormatter.format(result.lastEmi, currency)}"
        else -> CurrencyFormatter.format(result.monthlyEmi, currency, true)
    }

    // 1. Primary Hero KPI Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(SlateNavy, TealPrimaryDark)
                )
            )
            .padding(18.dp)
            .testTag("calculator_result_hero")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headlineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA7F3E8)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = headlineValue,
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${result.actualTenureMonths}m",
                        style = MonoSmallStyle,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat("PRINCIPAL", CurrencyFormatter.format(result.totalPrincipal, currency), Color(0xFF5EEAD4))
                HeroStat("TOTAL INTEREST", CurrencyFormatter.format(result.totalInterest, currency), Color(0xFFFCD34D))
                HeroStat("TOTAL PAYMENT", CurrencyFormatter.format(result.totalPayment, currency), Color.White)
            }

            if (result.totalFees > 0.0 || (category?.secured == true && input.assetValue > 0.0)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("EFFECTIVE COST (INCL. FEES)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            Text(
                                CurrencyFormatter.format(result.effectiveTotalCost, currency),
                                style = MonoMediumStyle,
                                color = Color.White
                            )
                        }
                        if (input.assetValue > 0.0 && (category?.secured == true || flags.contains("ASSET_MARGIN_SHARE"))) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LTV / DOWN PAYMENT", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    "${String.format("%.1f", result.ltvPercent)}% • ${CurrencyFormatter.formatCompact(result.downPayment, currency)}",
                                    style = MonoSmallStyle,
                                    color = if (result.isLtvExceeded) Color(0xFFFCA5A5) else Color(0xFF5EEAD4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // 2. Action Row: Save, Compare, CSV, PDF
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSaveLoanClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("calc_save_loan_btn")
        ) {
            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save Loan", maxLines = 1)
        }
        OutlinedButton(
            onClick = onAddToCompareClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("calc_add_compare_btn")
        ) {
            Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Compare", maxLines = 1)
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onExportCsvClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("calc_export_csv_btn")
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export CSV")
        }
        OutlinedButton(
            onClick = onExportPdfClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("calc_export_pdf_btn")
        ) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export PDF")
        }
    }

    // 3. Variant-Specific Insights Cards
    val insights = result.insights

    insights.prepaymentImpact?.let { prep ->
        Spacer(Modifier.height(12.dp))
        FintechCard(
            containerColor = SavingsGreen.copy(alpha = 0.08f),
            borderColor = SavingsGreen.copy(alpha = 0.35f)
        ) {
            SectionAccentHeader(
                title = "Prepayment Impact",
                badge = "SAVED ${prep.monthsSaved}m",
                accentColor = SavingsGreen
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Interest Saved", CurrencyFormatter.format(prep.interestSaved, currency), SavingsGreen)
                InsightMiniStat("Prepay Fee", CurrencyFormatter.format(prep.prepaymentFees, currency), InterestAmber)
                InsightMiniStat("Net Savings", CurrencyFormatter.format(prep.netSavings, currency), SavingsGreen)
            }
        }
    }

    if (insights.offsetInterestSaved > 0.0) {
        Spacer(Modifier.height(12.dp))
        FintechCard(
            containerColor = PrincipalEmerald.copy(alpha = 0.08f),
            borderColor = PrincipalEmerald.copy(alpha = 0.35f)
        ) {
            SectionAccentHeader("Offset Account Savings", badge = "ACTIVE", accentColor = PrincipalEmerald)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Parking ${CurrencyFormatter.format(input.offsetBalance, currency)} in your linked offset account saves ${CurrencyFormatter.format(insights.offsetInterestSaved, currency)} in total interest while staying liquid.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (insights.moratoriumExtraInterest > 0.0) {
        Spacer(Modifier.height(12.dp))
        FintechCard(
            containerColor = InterestAmber.copy(alpha = 0.08f),
            borderColor = InterestAmber.copy(alpha = 0.35f)
        ) {
            SectionAccentHeader("Compound Moratorium Capitalization", badge = "+PRINCIPAL", accentColor = InterestAmber)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Unpaid interest of ${CurrencyFormatter.format(insights.moratoriumExtraInterest, currency)} during the ${input.moratoriumMonths}-month moratorium was capitalized into principal before EMIs began.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    insights.flatVsReducing?.let { flat ->
        Spacer(Modifier.height(12.dp))
        FintechCard(
            containerColor = InterestAmber.copy(alpha = 0.07f),
            borderColor = InterestAmber.copy(alpha = 0.35f)
        ) {
            SectionAccentHeader(
                title = "Flat vs Reducing Rate Truth",
                badge = "EAR ${String.format("%.2f", flat.effectiveAnnualRatePercent)}%",
                accentColor = InterestAmber
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Flat EMI", CurrencyFormatter.format(flat.flatMonthlyEmi, currency), DangerRed)
                InsightMiniStat("Reducing EMI", CurrencyFormatter.format(flat.reducingMonthlyEmi, currency), PrincipalEmerald)
                InsightMiniStat("Extra Flat Cost", CurrencyFormatter.format(flat.extraInterestCost, currency), InterestAmber)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A ${String.format("%.2f", flat.quotedRate)}% flat rate costs the same as a ${String.format("%.2f", flat.effectiveAnnualRatePercent)}% reducing-balance loan — costing ${CurrencyFormatter.format(flat.extraInterestCost, currency)} extra in interest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (insights.sensitivityRows.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FintechCard {
            SectionAccentHeader("Floating Rate Sensitivity (±1% / ±2%)", badge = "WHAT-IF")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateNavy, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text("SHIFT", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.8f))
                Text("RATE", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.9f))
                Text("EMI", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                Text("INT DIFF", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
            }
            insights.sensitivityRows.forEach { row ->
                val isBase = row.deltaPercent == 0.0
                val deltaLabel = when {
                    row.deltaPercent > 0 -> "+${String.format("%.0f", row.deltaPercent)}%"
                    row.deltaPercent < 0 -> "${String.format("%.0f", row.deltaPercent)}%"
                    else -> "Base"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isBase) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(deltaLabel, style = MonoSmallStyle, fontWeight = if (isBase) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(0.8f))
                    Text("${String.format("%.2f", row.effectiveRate)}%", style = MonoSmallStyle, modifier = Modifier.weight(0.9f))
                    Text(CurrencyFormatter.format(row.monthlyEmi, currency), style = MonoSmallStyle, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    Text(
                        text = (if (row.interestDiff > 0) "+" else "") + CurrencyFormatter.formatCompact(row.interestDiff, currency),
                        style = MonoSmallStyle,
                        color = when {
                            row.interestDiff > 0 -> DangerRed
                            row.interestDiff < 0 -> SavingsGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.End
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    insights.balanceTransferResult?.let { bt ->
        Spacer(Modifier.height(12.dp))
        val worthwhile = bt.netSavings > 0.0
        val tint = if (worthwhile) SavingsGreen else DangerRed
        FintechCard(containerColor = tint.copy(alpha = 0.07f), borderColor = tint.copy(alpha = 0.35f)) {
            SectionAccentHeader(
                title = "Balance Transfer Analysis",
                badge = bt.breakEvenMonth?.let { "BREAK-EVEN M$it" } ?: "NO SAVINGS",
                accentColor = tint
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Old → New EMI", "${CurrencyFormatter.format(bt.currentEmi, currency)} → ${CurrencyFormatter.format(bt.newEmi, currency)}", MaterialTheme.colorScheme.onSurface)
                InsightMiniStat("Net Savings", CurrencyFormatter.format(bt.netSavings, currency), tint)
            }
        }
    }

    insights.topUpResult?.let { tu ->
        Spacer(Modifier.height(12.dp))
        FintechCard {
            SectionAccentHeader("Top-Up Blended EMI", badge = "COMBINED")
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Existing EMI", CurrencyFormatter.format(tu.existingEmi, currency), MaterialTheme.colorScheme.onSurface)
                InsightMiniStat("Top-Up EMI", CurrencyFormatter.format(tu.topUpEmi, currency), InterestAmber)
                InsightMiniStat("Blended EMI", CurrencyFormatter.format(tu.blendedEmi, currency), PrincipalEmerald)
            }
        }
    }

    insights.fdNetCost?.let { fd ->
        Spacer(Modifier.height(12.dp))
        FintechCard(containerColor = PrincipalEmerald.copy(alpha = 0.07f), borderColor = PrincipalEmerald.copy(alpha = 0.35f)) {
            SectionAccentHeader(
                title = "Loan Against FD — Net Cost",
                badge = "SPREAD ${String.format("%.2f", fd.effectiveNetRatePercent)}%",
                accentColor = PrincipalEmerald
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Loan Interest", CurrencyFormatter.format(fd.loanInterestPaid, currency), InterestAmber)
                InsightMiniStat("FD Earned", CurrencyFormatter.format(fd.fdInterestEarned, currency), SavingsGreen)
                InsightMiniStat("Net Cost", CurrencyFormatter.format(fd.netInterestCost, currency), PrincipalEmerald)
            }
        }
    }

    insights.billDiscountResult?.let { bd ->
        Spacer(Modifier.height(12.dp))
        FintechCard {
            SectionAccentHeader(
                title = "Invoice Discounting Summary",
                badge = "APR ${String.format("%.1f", bd.effectiveAnnualizedCostPercent)}%",
                accentColor = InterestAmber
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Advance", CurrencyFormatter.format(bd.advanceAmount, currency), PrincipalEmerald)
                InsightMiniStat("Discount Fee", CurrencyFormatter.format(bd.discountFee, currency), InterestAmber)
                InsightMiniStat("Net Upfront", CurrencyFormatter.format(bd.netUpfrontDisbursed, currency), MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    insights.marginCallResult?.let { mc ->
        Spacer(Modifier.height(12.dp))
        val tint = if (mc.isMarginCallTriggered || mc.dropPercentToMarginCall < 15.0) DangerRed else InterestAmber
        FintechCard(containerColor = tint.copy(alpha = 0.08f), borderColor = tint.copy(alpha = 0.4f)) {
            SectionAccentHeader(
                title = "Margin Call Monitor",
                badge = if (mc.isMarginCallTriggered) "CALL TRIGGERED" else "BUFFER ${String.format("%.1f", mc.dropPercentToMarginCall)}%",
                accentColor = tint
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (mc.isMarginCallTriggered) {
                    "Margin Call active! Pledged shares (${CurrencyFormatter.format(mc.pledgedShareValue, currency)}) are at or below required collateral value (${CurrencyFormatter.format(mc.marginCallTriggerValue, currency)})."
                } else {
                    "Current LTV is ${String.format("%.1f", mc.currentLtvPercent)}%. If pledged share value drops by ${String.format("%.1f", mc.dropPercentToMarginCall)}% to ${CurrencyFormatter.format(mc.marginCallTriggerValue, currency)}, a Margin Call will trigger."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    insights.goldSummary?.let { gold ->
        Spacer(Modifier.height(12.dp))
        FintechCard {
            SectionAccentHeader("Gold Loan — ${gold.modeLabel}", badge = "MATURITY", accentColor = InterestAmber)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMiniStat("Monthly Outflow", CurrencyFormatter.format(gold.monthlyOutflow, currency), PrincipalEmerald)
                InsightMiniStat("At Maturity", CurrencyFormatter.format(gold.maturityLumpSum, currency), InterestAmber)
                InsightMiniStat("Total Payable", CurrencyFormatter.format(gold.totalPayable, currency), MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    // 4. Visual Breakdown & Styled Amortization Schedule (Yearly | Monthly | Graph)
    Spacer(Modifier.height(14.dp))
    var viewTab by remember { mutableIntStateOf(2) } // 0 = Yearly, 1 = Monthly, 2 = Graph
    val tabLabels = listOf("Yearly", "Monthly", "Graph")

    FintechCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            tabLabels.forEachIndexed { idx, label ->
                val selected = viewTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { viewTab = idx }
                        .padding(vertical = 8.dp)
                        .testTag("schedule_tab_${label.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (viewTab) {
            2 -> {
                PrincipalInterestDonutChart(
                    principal = result.totalPrincipal,
                    interest = result.totalInterest,
                    fees = result.totalFees,
                    currency = currency
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                BalanceOverTimeLineChart(
                    yearlySummaries = result.yearlySummaries,
                    startingBalance = result.totalPrincipal,
                    currency = currency
                )
            }
            0 -> {
                YearlyScheduleTable(result = result, currency = currency)
            }
            1 -> {
                MonthlyScheduleTable(result = result, currency = currency)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBBD6D2))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MonoMediumStyle, color = valueColor)
    }
}

@Composable
private fun InsightMiniStat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoSmallStyle, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun YearlyScheduleTable(result: CalculationResult, currency: CurrencyCode) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateNavy, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text("YEAR", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(0.9f))
            Text("PRINCIPAL", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
            Text("INTEREST", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
            Text("BALANCE", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        }
        result.yearlySummaries.forEachIndexed { idx, ys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Yr ${ys.yearIndex}", style = MonoSmallStyle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.9f))
                Text(CurrencyFormatter.format(ys.principalPaid, currency), style = MonoSmallStyle, color = PrincipalEmerald, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                Text(CurrencyFormatter.format(ys.interestPaid, currency), style = MonoSmallStyle, color = InterestAmber, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                Text(CurrencyFormatter.format(ys.endingBalance, currency), style = MonoSmallStyle, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MonthlyScheduleTable(result: CalculationResult, currency: CurrencyCode) {
    var expandedYearsCount by remember(result.yearlySummaries.size) { mutableIntStateOf(2) }
    val visibleYears = result.yearlySummaries.take(expandedYearsCount)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visibleYears.forEach { ys ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Dark Slate Year Banner (inspired by screenshot 2!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateNavy)
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ys.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Column Headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text("MONTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.85f))
                    Text("PRINCIPAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                    Text("INTEREST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                    Text("BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.15f), textAlign = TextAlign.End)
                }

                ys.rows.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.85f)) {
                            Text(r.monthLabel, style = MonoSmallStyle, color = MaterialTheme.colorScheme.onSurface)
                            if (r.phaseLabel != "EMI") {
                                Text(r.phaseLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            CurrencyFormatter.format(r.principalPaid, currency),
                            style = MonoSmallStyle,
                            color = PrincipalEmerald,
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            CurrencyFormatter.format(r.interestPaid, currency),
                            style = MonoSmallStyle,
                            color = InterestAmber,
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            CurrencyFormatter.format(r.closingBalance, currency),
                            style = MonoSmallStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.15f),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Dark Navy Total Row (matching screenshot 2!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateNavy)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.85f))
                    Text(CurrencyFormatter.format(ys.principalPaid, currency), style = MonoSmallStyle, color = Color(0xFF5EEAD4), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                    Text(CurrencyFormatter.format(ys.interestPaid, currency), style = MonoSmallStyle, color = Color(0xFFFCD34D), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                    Text(CurrencyFormatter.format(ys.endingBalance, currency), style = MonoSmallStyle, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f), textAlign = TextAlign.End)
                }
            }
        }

        if (expandedYearsCount < result.yearlySummaries.size) {
            OutlinedButton(
                onClick = { expandedYearsCount = minOf(result.yearlySummaries.size, expandedYearsCount + 5) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show More Years (${result.yearlySummaries.size - expandedYearsCount} remaining)")
            }
        }
    }
}
