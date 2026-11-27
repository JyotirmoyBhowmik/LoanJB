package com.jyotirmoyb.loan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark
import com.jyotirmoyb.loan.viewmodel.PortfolioMetrics
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary

@Composable
fun MyLoansScreen(
    loans: List<TrackedLoanSummary>,
    metrics: PortfolioMetrics,
    monthlyIncome: Double,
    currency: CurrencyCode,
    onMarkNextEmiPaid: (TrackedLoanSummary) -> Unit,
    onToggleEmiPaid: (TrackedLoanSummary, Int) -> Unit,
    onLogPrepayment: (TrackedLoanSummary, Int, Double, Boolean) -> Unit,
    onLogRateChange: (TrackedLoanSummary, Int, Double) -> Unit,
    onToggleAutoDebitNach: (TrackedLoanSummary, Boolean) -> Unit,
    onUpdateEmiDay: (TrackedLoanSummary, Int) -> Unit,
    onToggleReminder: (TrackedLoanSummary, Boolean, Int) -> Unit,
    onToggleChecklist: (TrackedLoanSummary, Int) -> Unit,
    onAddToCompare: (TrackedLoanSummary) -> Unit,
    onDeleteLoan: (Long) -> Unit,
    onOpenCalculator: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLoanId by remember { mutableStateOf<Long?>(null) }
    val selectedLoan = loans.firstOrNull { it.entity.id == selectedLoanId }

    if (selectedLoan != null) {
        BackHandler { selectedLoanId = null }
        TrackedLoanDetailScreen(
            loan = selectedLoan,
            currency = currency,
            onBack = { selectedLoanId = null },
            onToggleEmiPaid = { m -> onToggleEmiPaid(selectedLoan, m) },
            onLogPrepayment = { m, amt, redTen -> onLogPrepayment(selectedLoan, m, amt, redTen) },
            onLogRateChange = { afterM, rate -> onLogRateChange(selectedLoan, afterM, rate) },
            onToggleAutoDebitNach = { en -> onToggleAutoDebitNach(selectedLoan, en) },
            onUpdateEmiDay = { day -> onUpdateEmiDay(selectedLoan, day) },
            onToggleReminder = { en, days -> onToggleReminder(selectedLoan, en, days) },
            onToggleChecklist = { idx -> onToggleChecklist(selectedLoan, idx) },
            onAddToCompare = { onAddToCompare(selectedLoan) },
            onDelete = {
                selectedLoanId = null
                onDeleteLoan(selectedLoan.entity.id)
            },
            onShowMessage = onShowMessage,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "portfolio_dashboard") {
            PortfolioDashboardCard(
                metrics = metrics,
                monthlyIncome = monthlyIncome,
                currency = currency
            )
        }

        if (loans.isEmpty()) {
            item(key = "empty_loans_card") {
                FintechCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            stringResource(R.string.ui_txt_no_saved_loans_yet),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.ui_txt_calculate_any_loan_in_the_emi_calc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onOpenCalculator,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("myloans_empty_calc_btn")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_txt_plan_save_a_loan))
                        }
                    }
                }
            }
        } else {
            items(
                items = loans,
                key = { it.entity.id },
                contentType = { "saved_loan_card" }
            ) { loan ->
                SavedLoanListCard(
                    loan = loan,
                    currency = currency,
                    onClick = { selectedLoanId = loan.entity.id },
                    onQuickPayNext = { onMarkNextEmiPaid(loan) }
                )
            }
        }

        item(key = "myloans_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PortfolioDashboardCard(metrics: PortfolioMetrics, monthlyIncome: Double, currency: CurrencyCode) {
    val dtiColor = when {
        metrics.dtiPercent <= 35.0 -> Color(0xFF5EEAD4)
        metrics.dtiPercent <= 50.0 -> Color(0xFFFCD34D)
        else -> Color(0xFFFCA5A5)
    }
    val dtiLabel = when {
        monthlyIncome <= 0.0 -> "Set Income in Profile"
        metrics.dtiPercent <= 35.0 -> "Healthy DTI"
        metrics.dtiPercent <= 50.0 -> "Moderate Burden"
        else -> "High DTI Alert"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(SlateNavy, TealPrimaryDark)))
            .padding(18.dp)
            .testTag("myloans_portfolio_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui_txt_india_debt_portfolio),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7F3E8)
                )
                Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        stringResource(R.string.ui_txt_active, metrics.activeLoanCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ui_txt_total_outstanding_debt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        CurrencyFormatter.format(metrics.totalOutstandingDebt, currency),
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.ui_txt_interest_saved),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA7F3E8)
                    )
                    Text(
                        CurrencyFormatter.format(metrics.totalInterestSaved, currency),
                        style = MonoMediumStyle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5EEAD4),
                        modifier = Modifier.testTag("myloans_interest_saved_value")
                    )
                    if (metrics.totalMonthsSaved > 0) {
                        Text(
                            stringResource(R.string.ui_txt_mo_s_cut, metrics.totalMonthsSaved),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F3E8)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        stringResource(R.string.ui_txt_monthly_obligations),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        CurrencyFormatter.format(metrics.totalMonthlyEmiBurden, currency),
                        style = MonoMediumStyle,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.ui_txt_debt_to_income_dti),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        stringResource(R.string.ui_txt_fmt_6a68b0, String.format("%.1f", metrics.dtiPercent), dtiLabel),
                        style = MonoSmallStyle,
                        fontWeight = FontWeight.Bold,
                        color = dtiColor
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (metrics.dtiPercent.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = dtiColor,
                trackColor = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun SavedLoanListCard(loan: TrackedLoanSummary, currency: CurrencyCode, onClick: () -> Unit, onQuickPayNext: () -> Unit) {
    FintechCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("saved_loan_card_${loan.entity.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        loan.entity.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (loan.autoDebitNach) {
                        Surface(
                            color = PrincipalEmerald.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                stringResource(R.string.ui_txt_nach),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrincipalEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.ui_txt_day, loan.entity.lender, loan.entity.categoryName, loan.emiDayOfMonth),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    loan.entity.variantSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.ui_txt_next_due),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(loan.nextDueDateLabel, style = MonoSmallStyle, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    stringResource(R.string.ui_txt_outstanding),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(CurrencyFormatter.format(loan.outstandingPrincipal, currency), style = MonoMediumStyle)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.ui_txt_paid_total_emis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.ui_txt_fmt_077e75, loan.paidMonthNumbers.size, loan.result.schedule.size),
                    style = MonoMediumStyle,
                    color = PrincipalEmerald
                )
            }
        }

        if (loan.interestSaved > 0.0 || loan.monthsSaved > 0) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = SavingsGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Savings, contentDescription = null, tint = SavingsGreen, modifier = Modifier.size(14.dp))
                    val savedBadgeText = if (loan.monthsSaved > 0) {
                        stringResource(
                            R.string.ui_loan_saved_with_months,
                            CurrencyFormatter.format(loan.interestSaved, currency),
                            loan.monthsSaved
                        )
                    } else {
                        stringResource(
                            R.string.ui_loan_saved_only,
                            CurrencyFormatter.format(loan.interestSaved, currency)
                        )
                    }
                    Text(
                        text = savedBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SavingsGreen
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { loan.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = PrincipalEmerald
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(
                    R.string.ui_txt_emi_08e871,
                    CurrencyFormatter.format(loan.result.monthlyEmi, currency),
                    String.format("%.2f", loan.input.annualRate)
                ),
                style = MonoSmallStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loan.nextDueMonthNumber != null) {
                Button(
                    onClick = onQuickPayNext,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mark_paid_btn_${loan.entity.id}")
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.ui_txt_mark_m_paid, loan.nextDueMonthNumber), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
