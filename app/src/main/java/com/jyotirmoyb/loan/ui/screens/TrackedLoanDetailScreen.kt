package com.jyotirmoyb.loan.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanTrackerEngine
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.util.ScheduleExporter
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary

@Composable
internal fun TrackedLoanDetailScreen(
    loan: TrackedLoanSummary,
    currency: CurrencyCode,
    onBack: () -> Unit,
    onToggleEmiPaid: (Int) -> Unit,
    onLogPrepayment: (Int, Double, Boolean) -> Unit,
    onLogRateChange: (Int, Double) -> Unit,
    onToggleAutoDebitNach: (Boolean) -> Unit,
    onUpdateEmiDay: (Int) -> Unit,
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
    var showEmiDayDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var visibleMonths by remember { mutableIntStateOf(24) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleReminder(true, loan.entity.reminderDaysBefore)
        } else {
            onShowMessage("Notification permission denied. Enable notifications in Android Settings for EMI alerts.")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "detail_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("loan_detail_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(loan.entity.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.ui_txt_fmt_4ed101, loan.entity.lender, loan.entity.variantSummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete loan", tint = DangerRed)
                }
            }
        }

        item(key = "detail_summary") {
            FintechCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            stringResource(R.string.ui_txt_outstanding_154667),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.format(loan.outstandingPrincipal, currency),
                            style = MonoLargeStyle,
                            color = PrincipalEmerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.ui_txt_next_due_date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        stringResource(
                            R.string.ui_txt_paid_emis,
                            CurrencyFormatter.format(loan.totalPaidSoFar, currency),
                            loan.paidMonthNumbers.size
                        ),
                        style = MonoSmallStyle,
                        color = SavingsGreen
                    )
                    Text(
                        stringResource(
                            R.string.ui_txt_remaining_emis,
                            (loan.result.schedule.size - loan.paidMonthNumbers.size).coerceAtLeast(0)
                        ),
                        style = MonoSmallStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loan.interestSaved > 0.0 || loan.monthsSaved > 0) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = SavingsGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SavingsGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Savings, contentDescription = null, tint = SavingsGreen, modifier = Modifier.size(18.dp))
                            val savedBannerText = if (loan.monthsSaved > 0) {
                                stringResource(
                                    R.string.ui_tracked_saved_with_months,
                                    CurrencyFormatter.format(loan.interestSaved, currency),
                                    loan.monthsSaved
                                )
                            } else {
                                stringResource(
                                    R.string.ui_tracked_saved_only,
                                    CurrencyFormatter.format(loan.interestSaved, currency)
                                )
                            }
                            Text(
                                text = savedBannerText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SavingsGreen
                            )
                        }
                    }
                }
            }
        }

        item(key = "detail_actions") {
            FintechCard {
                SectionAccentHeader("Loan Actions & Events", badge = "LIVE RECALC")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showPrepayDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("loan_log_prepay_btn")
                    ) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.ui_txt_prepayment_1ff8e5), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showRateDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("loan_log_rate_btn")
                    ) {
                        Icon(Icons.Outlined.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.ui_txt_rate_reset), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val r = ScheduleExporter.exportBrandedLoanReportPdfAndShare(context, loan, currency)
                            if (r.isFailure) onShowMessage("Branded PDF report export failed")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .minimumInteractiveComponentSize()
                            .testTag("loan_export_branded_pdf_btn")
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_txt_pdf_report), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val r = ScheduleExporter.exportCsvAndShare(context, loan.entity.nickname, loan.result, currency)
                            if (r.isFailure) onShowMessage("CSV export failed")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_txt_csv))
                    }
                    OutlinedButton(
                        onClick = onAddToCompare,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_txt_compare))
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
                        Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = PrincipalEmerald)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.ui_txt_auto_debit_nach_mandate),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (loan.autoDebitNach) {
                                    "Active — keep account funded before day ${loan.emiDayOfMonth}"
                                } else {
                                    "Manual EMI payment mode"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = loan.autoDebitNach,
                        onCheckedChange = onToggleAutoDebitNach,
                        modifier = Modifier.testTag("loan_nach_switch")
                    )
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { showEmiDayDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.ui_txt_monthly_emi_due_date),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.ui_txt_day_of_every_month_tap_to_change, loan.emiDayOfMonth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { showEmiDayDialog = true }) {
                        Text(stringResource(R.string.ui_txt_edit))
                    }
                }

                Spacer(Modifier.height(6.dp))
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
                            Text(
                                stringResource(R.string.ui_txt_next_due_alert_notification),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.ui_txt_local_alert_days_before_due_date_e, loan.entity.reminderDaysBefore),
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
                        },
                        modifier = Modifier.testTag("loan_reminder_switch")
                    )
                }
            }
        }

        item(key = "detail_checklist") {
            val isVehicle = LoanTrackerEngine.isVehicleLoan(loan.entity.categoryId, loan.entity.categoryName)
            FintechCard(
                containerColor = InterestAmber.copy(alpha = 0.06f),
                borderColor = InterestAmber.copy(alpha = 0.35f)
            ) {
                SectionAccentHeader(
                    title = if (isVehicle) "Loan Closure & RTO Form 35 Checklist" else "Loan Closure Checklist (NOC, Lien & CIBIL)",
                    badge = "${loan.completedChecklist.size}/${loan.checklistItems.size}",
                    accentColor = InterestAmber
                )
                Spacer(Modifier.height(6.dp))
                loan.checklistItems.forEachIndexed { idx, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
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

        item(key = "detail_payment_ledger") {
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
                            .minimumInteractiveComponentSize()
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
                                stringResource(R.string.ui_txt_m_dea6f6, row.monthNumber, row.monthLabel, row.phaseLabel),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isPaid) SavingsGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(
                                    R.string.ui_txt_p_i_rate,
                                    CurrencyFormatter.format(row.principalPaid, currency),
                                    CurrencyFormatter.format(row.interestPaid, currency),
                                    String.format("%.2f", row.activeRate)
                                ),
                                style = MonoSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(CurrencyFormatter.format(row.emiPaid, currency), style = MonoSmallStyle, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.ui_txt_bal, CurrencyFormatter.formatCompact(row.closingBalance, currency)),
                                style = MonoSmallStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (visibleMonths < loan.result.schedule.size) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { visibleMonths += 24 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.ui_txt_load_more_months_left, loan.result.schedule.size - visibleMonths))
                    }
                }
            }
        }
    }

    if (showPrepayDialog) {
        TrackedLoanPrepayDialog(
            loan = loan,
            currency = currency,
            onDismiss = { showPrepayDialog = false },
            onConfirm = onLogPrepayment
        )
    }

    if (showRateDialog) {
        TrackedLoanRateDialog(
            loan = loan,
            onDismiss = { showRateDialog = false },
            onConfirm = onLogRateChange
        )
    }

    if (showEmiDayDialog) {
        TrackedLoanEmiDayDialog(
            loan = loan,
            onDismiss = { showEmiDayDialog = false },
            onConfirm = onUpdateEmiDay
        )
    }

    if (showDeleteConfirm) {
        TrackedLoanDeleteDialog(
            nickname = loan.entity.nickname,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = onDelete
        )
    }
}
