package com.jyotirmoyb.loan.features.emi.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.AmortizationRow
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.YearlySummary
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrepaymentHighlightDark
import com.jyotirmoyb.loan.ui.theme.PrepaymentHighlightLight
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

private sealed interface MonthlyListItem {
    val key: String
    data class YearHeader(val summary: YearlySummary) : MonthlyListItem {
        override val key: String = "yh_${summary.yearIndex}"
    }
    data class MonthData(val row: AmortizationRow) : MonthlyListItem {
        override val key: String = "m_${row.monthNumber}"
    }
    data class YearFooter(val summary: YearlySummary) : MonthlyListItem {
        override val key: String = "yf_${summary.yearIndex}"
    }
}

@Composable
fun MonthlyScheduleTab(
    result: CalculationResult,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val prepayBg = if (isDark) PrepaymentHighlightDark else PrepaymentHighlightLight

    // Flatten into virtualized LazyColumn items so 360-row schedules scroll smoothly at 60fps
    val listItems = remember(result.yearlySummaries) {
        buildList {
            result.yearlySummaries.forEach { ys ->
                add(MonthlyListItem.YearHeader(ys))
                ys.rows.forEach { row ->
                    add(MonthlyListItem.MonthData(row))
                }
                add(MonthlyListItem.YearFooter(ys))
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("results_monthly_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(listItems, key = { it.key }) { item ->
            when (item) {
                is MonthlyListItem.YearHeader -> {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    ) {
                        // Dark Navy Year Banner: "YEAR 1 · Oct – Sep"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateNavy)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFA7F3E8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.summary.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // 4-Column Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.results_col_month),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.85f)
                            )
                            Text(
                                text = stringResource(R.string.results_col_principal),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = stringResource(R.string.results_col_interest),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.0f)
                            )
                            Text(
                                text = stringResource(R.string.results_col_balance),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.15f)
                            )
                        }
                    }
                }

                is MonthlyListItem.MonthData -> {
                    val row = item.row
                    val hasPrepayment = row.extraPrepayment > 0.01
                    val monthShort = row.monthLabel.substringBefore(" ")

                    Surface(
                        color = if (hasPrepayment) prepayBg else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (hasPrepayment) "monthly_prepay_row_${row.monthNumber}" else "monthly_row_${row.monthNumber}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(0.85f)) {
                                Text(
                                    text = monthShort,
                                    style = MonoSmallStyle,
                                    fontWeight = if (hasPrepayment) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (hasPrepayment) {
                                    Text(
                                        text = "+Prepay",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimaryDark
                                    )
                                } else if (row.phaseLabel != "EMI") {
                                    Text(
                                        text = row.phaseLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = CurrencyFormatter.format(row.principalPaid, currency),
                                style = MonoSmallStyle,
                                fontWeight = if (hasPrepayment) FontWeight.Bold else FontWeight.Medium,
                                color = PrincipalEmerald,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = CurrencyFormatter.format(row.interestPaid, currency),
                                style = MonoSmallStyle,
                                color = InterestAmber,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.0f)
                            )
                            Text(
                                text = CurrencyFormatter.format(row.closingBalance, currency),
                                style = MonoSmallStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.15f)
                            )
                        }
                    }
                }

                is MonthlyListItem.YearFooter -> {
                    val ys = item.summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(SlateNavy)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.results_total_row),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(0.85f)
                        )
                        Text(
                            text = CurrencyFormatter.format(ys.principalPaid, currency),
                            style = MonoSmallStyle,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5EEAD4),
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = CurrencyFormatter.format(ys.interestPaid, currency),
                            style = MonoSmallStyle,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCD34D),
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.0f)
                        )
                        Text(
                            text = CurrencyFormatter.format(ys.endingBalance, currency),
                            style = MonoSmallStyle,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.15f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item(key = "monthly_disclaimer") {
            Spacer(Modifier.height(12.dp))
            DisclaimerBanner()
            Spacer(Modifier.height(16.dp))
        }
    }
}
