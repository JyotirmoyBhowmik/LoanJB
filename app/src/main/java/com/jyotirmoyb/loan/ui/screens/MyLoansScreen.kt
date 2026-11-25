package com.jyotirmoyb.loan.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
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
import com.jyotirmoyb.loan.util.ScheduleExporter
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
        // Portfolio Dashboard Hero
        item {
            PortfolioDashboardCard(
                metrics = metrics,
                monthlyIncome = monthlyIncome,
                currency = currency
            )
        }

        if (loans.isEmpty()) {
            item {
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
                            "No Saved Loans Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Calculate any loan in the Calculator tab and tap \"Save Loan\" to track EMIs, reminders, prepayments, and debt-to-income ratio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onOpenCalculator,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("myloans_empty_calc_btn")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Plan & Save a Loan")
                        }
                    }
                }
            }
        } else {
            items(loans, key = { it.entity.id }) { loan ->
                SavedLoanListCard(
                    loan = loan,
                    currency = currency,
                    onClick = { selectedLoanId = loan.entity.id },
                    onQuickPayNext = { onMarkNextEmiPaid(loan) }
                )
            }
        }

        item {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PortfolioDashboardCard(
    metrics: PortfolioMetrics,
    monthlyIncome: Double,
    currency: CurrencyCode
) {
    val dtiColor = when {
        metrics.dtiPercent <= 35.0 -> Color(0xFF5EEAD4)
        metrics.dtiPercent <= 50.0 -> Color(0xFFFCD34D)
        else -> Color(0xFFFCA5A5)
    }
    val dtiLabel = when {
        monthlyIncome <= 0.0 -> "Set Income in Settings"
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
                Text("DEBT PORTFOLIO", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7F3E8))
                Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "${metrics.activeLoanCount} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text("Total Outstanding Principal", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                Text(
                    CurrencyFormatter.format(metrics.totalOutstandingDebt, currency),
                    style = MonoLargeStyle,
                    color = Color.White
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("MONTHLY EMI BURDEN", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                    Text(
                        CurrencyFormatter.format(metrics.totalMonthlyEmiBurden, currency),
                        style = MonoMediumStyle,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DEBT-TO-INCOME (DTI)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                    Text(
                        "${String.format("%.1f", metrics.dtiPercent)}% • $dtiLabel",
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
private fun SavedLoanListCard(
    loan: TrackedLoanSummary,
    currency: CurrencyCode,
    onClick: () -> Unit,
    onQuickPayNext: () -> Unit
) {
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
                Text(loan.entity.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${loan.entity.lender} • ${loan.entity.categoryName}",
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
                Text("Next Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(loan.nextDueDateLabel, style = MonoSmallStyle, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(loan.outstandingPrincipal, currency), style = MonoMediumStyle)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Paid / Total EMIs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${loan.paidMonthNumbers.size} / ${loan.result.schedule.size}",
                    style = MonoMediumStyle,
                    color = PrincipalEmerald
                )
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
                "EMI: ${CurrencyFormatter.format(loan.result.monthlyEmi, currency)} • ${String.format("%.2f", loan.input.annualRate)}%",
                style = MonoSmallStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loan.nextDueMonthNumber != null) {
                Button(
                    onClick = onQuickPayNext,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("mark_paid_btn_${loan.entity.id}")
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Mark M#${loan.nextDueMonthNumber} Paid", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TrackedLoanDetailScreen(
    loan: TrackedLoanSummary,
    currency: CurrencyCode,
    onBack: () -> Unit,
    onToggleEmiPaid: (Int) -> Unit,
    onLogPrepayment: (Int, Double, Boolean) -> Unit,
    onLogRateChange: (Int, Double) -> Unit,
    onToggleReminder: (Boolean, Int) -> Unit,
    onToggleChecklist: (Int) -> Unit,
    onAddToCompare: () -> Unit,
    onDelete: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPrepayDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var visibleMonths by remember { mutableIntStateOf(24) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleReminder(true, loan.entity.reminderDaysBefore)
        } else {
            onShowMessage("Notification permission denied. Reminders require notification access.")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("loan_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(loan.entity.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${loan.entity.lender} • ${loan.entity.variantSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete loan", tint = DangerRed)
                }
            }
        }

        // Progress & Summary Card
        item {
            FintechCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("OUTSTANDING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.format(loan.outstandingPrincipal, currency), style = MonoLargeStyle, color = PrincipalEmerald)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("NEXT DUE DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(loan.nextDueDateLabel, style = MonoMediumStyle)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { loan.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrincipalEmerald
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Paid: ${CurrencyFormatter.format(loan.totalPaidSoFar, currency)} (${loan.paidMonthNumbers.size} EMIs)",
                        style = MonoSmallStyle,
                        color = SavingsGreen
                    )
                    Text(
                        "Remaining: ${loan.result.schedule.size - loan.paidMonthNumbers.size} EMIs",
                        style = MonoSmallStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Actions: Log Prepayment, Log Rate Change, Reminder, Export, Compare
        item {
            FintechCard {
                SectionAccentHeader("Loan Actions & Events", badge = "LIVE RECALC")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showPrepayDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loan_log_prepay_btn")
                    ) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Prepayment", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showRateDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loan_log_rate_btn")
                    ) {
                        Icon(Icons.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Rate Change", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val r = ScheduleExporter.exportCsvAndShare(context, loan.entity.nickname, loan.result, currency)
                            if (r.isFailure) onShowMessage("CSV export failed")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            val r = ScheduleExporter.exportPdfAndShare(context, loan.entity.nickname, loan.entity.variantSummary, loan.result, currency)
                            if (r.isFailure) onShowMessage("PDF export failed")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = onAddToCompare,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Compare")
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("EMI Due Date Reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Local notification ${loan.entity.reminderDaysBefore} days before due date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = loan.entity.reminderEnabled,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleReminder(wantEnable, loan.entity.reminderDaysBefore)
                            }
                        }
                    )
                }
            }
        }

        // Hypothecation & NOC Checklist (for Auto/Vehicle or any category with checklist)
        if (loan.checklistItems.isNotEmpty()) {
            item {
                FintechCard(
                    containerColor = InterestAmber.copy(alpha = 0.06f),
                    borderColor = InterestAmber.copy(alpha = 0.35f)
                ) {
                    SectionAccentHeader(
                        title = "Hypothecation & NOC Release",
                        badge = "${loan.completedChecklist.size}/${loan.checklistItems.size}",
                        accentColor = InterestAmber
                    )
                    Spacer(Modifier.height(6.dp))
                    loan.checklistItems.forEachIndexed { idx, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklist(idx) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = loan.completedChecklist.contains(idx),
                                onCheckedChange = { onToggleChecklist(idx) }
                            )
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Interactive Payment Schedule (tap checkbox to mark paid/unpaid)
        item {
            FintechCard {
                SectionAccentHeader(
                    title = "Payment Ledger",
                    badge = "${loan.paidMonthNumbers.size}/${loan.result.schedule.size} Paid"
                )
                Spacer(Modifier.height(8.dp))
                loan.result.schedule.take(visibleMonths).forEach { row ->
                    val isPaid = loan.paidMonthNumbers.contains(row.monthNumber)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleEmiPaid(row.monthNumber) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isPaid,
                            onCheckedChange = { onToggleEmiPaid(row.monthNumber) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "M#${row.monthNumber} • ${row.monthLabel} (${row.phaseLabel})",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isPaid) SavingsGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "P: ${CurrencyFormatter.format(row.principalPaid, currency)} • I: ${CurrencyFormatter.format(row.interestPaid, currency)} • Rate: ${String.format("%.2f", row.activeRate)}%",
                                style = MonoSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(CurrencyFormatter.format(row.emiPaid, currency), style = MonoSmallStyle, fontWeight = FontWeight.Bold)
                            Text("Bal: ${CurrencyFormatter.formatCompact(row.closingBalance, currency)}", style = MonoSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (visibleMonths < loan.result.schedule.size) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { visibleMonths += 24 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load More Months (${loan.result.schedule.size - visibleMonths} left)")
                    }
                }
            }
        }
    }

    if (showPrepayDialog) {
        var amountText by remember { mutableStateOf("100000") }
        var monthText by remember { mutableStateOf((loan.nextDueMonthNumber ?: 1).toString()) }
        var reduceTenure by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showPrepayDialog = false },
            title = { Text("Log Actual Prepayment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField("Prepayment Amount (${currency.symbol})", amountText, { amountText = it })
                    NumericInputField("Applied in Month #", monthText, { monthText = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = reduceTenure, onClick = { reduceTenure = true }, label = { Text("Reduce Tenure") })
                        FilterChip(selected = !reduceTenure, onClick = { reduceTenure = false }, label = { Text("Reduce EMI") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val m = monthText.toIntOrNull() ?: (loan.nextDueMonthNumber ?: 1)
                    showPrepayDialog = false
                    onLogPrepayment(m, amt, reduceTenure)
                }) { Text("Recalculate") }
            },
            dismissButton = { TextButton(onClick = { showPrepayDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRateDialog) {
        var rateText by remember { mutableStateOf(loan.input.annualRate.toString()) }
        var afterMonthText by remember { mutableStateOf(((loan.nextDueMonthNumber ?: 1) - 1).coerceAtLeast(1).toString()) }

        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("Log Interest Rate Change") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField("New Annual Interest Rate (%)", rateText, { rateText = it })
                    NumericInputField("Effective After Month #", afterMonthText, { afterMonthText = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val r = rateText.toDoubleOrNull() ?: loan.input.annualRate
                    val m = afterMonthText.toIntOrNull() ?: 1
                    showRateDialog = false
                    onLogRateChange(m, r)
                }) { Text("Apply Rate") }
            },
            dismissButton = { TextButton(onClick = { showRateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Saved Loan?") },
            text = { Text("Remove \"${loan.entity.nickname}\" and cancel any scheduled reminders?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
