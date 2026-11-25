package com.jyotirmoyb.loan.features.emi.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.ui.components.BalanceOverTimeLineChart
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.PrincipalInterestDonutChart
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@Composable
fun GraphTab(
    input: CalculationInput,
    result: CalculationResult,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val insights = result.insights

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("results_graph_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "donut_chart") {
            FintechCard {
                SectionAccentHeader("Cost Breakdown", badge = "${result.actualTenureMonths}m")
                Spacer(Modifier.height(14.dp))
                PrincipalInterestDonutChart(
                    principal = result.totalPrincipal,
                    interest = result.totalInterest,
                    fees = result.totalFees,
                    currency = currency
                )
            }
        }

        item(key = "balance_line_chart") {
            FintechCard {
                BalanceOverTimeLineChart(
                    yearlySummaries = result.yearlySummaries,
                    startingBalance = result.totalPrincipal,
                    currency = currency
                )
            }
        }

        insights.prepaymentImpact?.let { prep ->
            item(key = "insight_prepay") {
                FintechCard(
                    containerColor = SavingsGreen.copy(alpha = 0.08f),
                    borderColor = SavingsGreen.copy(alpha = 0.35f)
                ) {
                    SectionAccentHeader(
                        title = "Prepayment Impact",
                        badge = "SAVED ${prep.monthsSaved}m",
                        accentColor = SavingsGreen
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GraphStat("Interest Saved", CurrencyFormatter.format(prep.interestSaved, currency), SavingsGreen)
                        GraphStat("Prepay Fee", CurrencyFormatter.format(prep.prepaymentFees, currency), InterestAmber)
                        GraphStat("Net Savings", CurrencyFormatter.format(prep.netSavings, currency), SavingsGreen)
                    }
                }
            }
        }

        if (insights.offsetInterestSaved > 0.0) {
            item(key = "insight_offset") {
                FintechCard(
                    containerColor = PrincipalEmerald.copy(alpha = 0.08f),
                    borderColor = PrincipalEmerald.copy(alpha = 0.35f)
                ) {
                    SectionAccentHeader("Offset Account Savings", badge = "ACTIVE", accentColor = PrincipalEmerald)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Parking ${CurrencyFormatter.format(input.offsetBalance, currency)} in your offset account saves ${CurrencyFormatter.format(insights.offsetInterestSaved, currency)} in total interest.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (insights.moratoriumExtraInterest > 0.0) {
            item(key = "insight_moratorium") {
                FintechCard(
                    containerColor = InterestAmber.copy(alpha = 0.08f),
                    borderColor = InterestAmber.copy(alpha = 0.35f)
                ) {
                    SectionAccentHeader("Compound Moratorium", badge = "+PRINCIPAL", accentColor = InterestAmber)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Unpaid interest of ${CurrencyFormatter.format(insights.moratoriumExtraInterest, currency)} during the ${input.moratoriumMonths}-month moratorium was capitalized into principal.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        insights.flatVsReducing?.let { flat ->
            item(key = "insight_flat") {
                FintechCard(
                    containerColor = InterestAmber.copy(alpha = 0.08f),
                    borderColor = InterestAmber.copy(alpha = 0.35f)
                ) {
                    SectionAccentHeader(
                        title = "Flat vs Reducing Rate Truth",
                        badge = "EAR ${String.format("%.2f", flat.effectiveAnnualRatePercent)}%",
                        accentColor = InterestAmber
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Flat EMI", CurrencyFormatter.format(flat.flatMonthlyEmi, currency), DangerRed)
                        GraphStat("Reducing EMI", CurrencyFormatter.format(flat.reducingMonthlyEmi, currency), PrincipalEmerald)
                        GraphStat("Extra Cost", CurrencyFormatter.format(flat.extraInterestCost, currency), InterestAmber)
                    }
                }
            }
        }

        if (insights.sensitivityRows.isNotEmpty()) {
            item(key = "insight_sensitivity") {
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
        }

        insights.balanceTransferResult?.let { bt ->
            item(key = "insight_bt") {
                val tint = if (bt.netSavings > 0.0) SavingsGreen else DangerRed
                FintechCard(containerColor = tint.copy(alpha = 0.07f), borderColor = tint.copy(alpha = 0.35f)) {
                    SectionAccentHeader(
                        title = "Balance Transfer Analysis",
                        badge = bt.breakEvenMonth?.let { "BREAK-EVEN M$it" } ?: "NO SAVINGS",
                        accentColor = tint
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Old → New EMI", "${CurrencyFormatter.format(bt.currentEmi, currency)} → ${CurrencyFormatter.format(bt.newEmi, currency)}", MaterialTheme.colorScheme.onSurface)
                        GraphStat("Net Savings", CurrencyFormatter.format(bt.netSavings, currency), tint)
                    }
                }
            }
        }

        insights.topUpResult?.let { tu ->
            item(key = "insight_topup") {
                FintechCard {
                    SectionAccentHeader("Top-Up Blended EMI", badge = "COMBINED")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Existing EMI", CurrencyFormatter.format(tu.existingEmi, currency), MaterialTheme.colorScheme.onSurface)
                        GraphStat("Top-Up EMI", CurrencyFormatter.format(tu.topUpEmi, currency), InterestAmber)
                        GraphStat("Blended EMI", CurrencyFormatter.format(tu.blendedEmi, currency), PrincipalEmerald)
                    }
                }
            }
        }

        insights.fdNetCost?.let { fd ->
            item(key = "insight_fd") {
                FintechCard(containerColor = PrincipalEmerald.copy(alpha = 0.07f), borderColor = PrincipalEmerald.copy(alpha = 0.35f)) {
                    SectionAccentHeader("Loan Against FD — Net Cost", badge = "SPREAD ${String.format("%.2f", fd.effectiveNetRatePercent)}%", accentColor = PrincipalEmerald)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Loan Interest", CurrencyFormatter.format(fd.loanInterestPaid, currency), InterestAmber)
                        GraphStat("FD Earned", CurrencyFormatter.format(fd.fdInterestEarned, currency), SavingsGreen)
                        GraphStat("Net Cost", CurrencyFormatter.format(fd.netInterestCost, currency), PrincipalEmerald)
                    }
                }
            }
        }

        insights.billDiscountResult?.let { bd ->
            item(key = "insight_bill") {
                FintechCard {
                    SectionAccentHeader("Invoice Discounting Summary", badge = "APR ${String.format("%.1f", bd.effectiveAnnualizedCostPercent)}%", accentColor = InterestAmber)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Advance", CurrencyFormatter.format(bd.advanceAmount, currency), PrincipalEmerald)
                        GraphStat("Discount Fee", CurrencyFormatter.format(bd.discountFee, currency), InterestAmber)
                        GraphStat("Net Upfront", CurrencyFormatter.format(bd.netUpfrontDisbursed, currency), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        insights.marginCallResult?.let { mc ->
            item(key = "insight_margin") {
                val tint = if (mc.isMarginCallTriggered || mc.dropPercentToMarginCall < 15.0) DangerRed else InterestAmber
                FintechCard(containerColor = tint.copy(alpha = 0.08f), borderColor = tint.copy(alpha = 0.4f)) {
                    SectionAccentHeader(
                        title = "Margin Call Monitor",
                        badge = if (mc.isMarginCallTriggered) "CALL TRIGGERED" else "BUFFER ${String.format("%.1f", mc.dropPercentToMarginCall)}%",
                        accentColor = tint
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (mc.isMarginCallTriggered) {
                            "Margin Call active! Pledged value (${CurrencyFormatter.format(mc.pledgedShareValue, currency)}) is below required collateral (${CurrencyFormatter.format(mc.marginCallTriggerValue, currency)})."
                        } else {
                            "Current LTV is ${String.format("%.1f", mc.currentLtvPercent)}%. A ${String.format("%.1f", mc.dropPercentToMarginCall)}% drop triggers a Margin Call."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        insights.goldSummary?.let { gold ->
            item(key = "insight_gold") {
                FintechCard {
                    SectionAccentHeader("Gold Loan — ${gold.modeLabel}", badge = "MATURITY", accentColor = InterestAmber)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GraphStat("Monthly", CurrencyFormatter.format(gold.monthlyOutflow, currency), PrincipalEmerald)
                        GraphStat("At Maturity", CurrencyFormatter.format(gold.maturityLumpSum, currency), InterestAmber)
                        GraphStat("Total", CurrencyFormatter.format(gold.totalPayable, currency), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item(key = "graph_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GraphStat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoSmallStyle, fontWeight = FontWeight.Bold, color = color)
    }
}
