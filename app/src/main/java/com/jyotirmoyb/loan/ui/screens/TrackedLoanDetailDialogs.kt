package com.jyotirmoyb.loan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary

@Composable
internal fun TrackedLoanPrepayDialog(
    loan: TrackedLoanSummary,
    currency: CurrencyCode,
    onDismiss: () -> Unit,
    onConfirm: (monthNumber: Int, amount: Double, reduceTenure: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf("100000") }
    var monthText by remember { mutableStateOf((loan.nextDueMonthNumber ?: 1).toString()) }
    var reduceTenure by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_txt_log_actual_prepayment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericInputField("Prepayment Amount (${currency.symbol})", amountText, { amountText = it })
                NumericInputField("Applied in Month #", monthText, { monthText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reduceTenure,
                        onClick = { reduceTenure = true },
                        label = { Text(stringResource(R.string.ui_txt_reduce_tenure)) }
                    )
                    FilterChip(
                        selected = !reduceTenure,
                        onClick = { reduceTenure = false },
                        label = { Text(stringResource(R.string.ui_txt_reduce_emi)) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val m = monthText.toIntOrNull() ?: (loan.nextDueMonthNumber ?: 1)
                    onDismiss()
                    onConfirm(m, amt, reduceTenure)
                }
            ) { Text(stringResource(R.string.ui_txt_recalculate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_txt_cancel)) }
        }
    )
}

@Composable
internal fun TrackedLoanRateDialog(loan: TrackedLoanSummary, onDismiss: () -> Unit, onConfirm: (afterMonth: Int, newRate: Double) -> Unit) {
    var rateText by remember { mutableStateOf(loan.input.annualRate.toString()) }
    var afterMonthText by remember { mutableStateOf(((loan.nextDueMonthNumber ?: 1) - 1).coerceAtLeast(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_txt_log_floating_rate_reset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericInputField("New Annual Interest Rate (%)", rateText, { rateText = it })
                NumericInputField("Effective After Month #", afterMonthText, { afterMonthText = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = rateText.toDoubleOrNull() ?: loan.input.annualRate
                    val m = afterMonthText.toIntOrNull() ?: 1
                    onDismiss()
                    onConfirm(m, r)
                }
            ) { Text(stringResource(R.string.ui_txt_apply_rate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_txt_cancel)) }
        }
    )
}

@Composable
internal fun TrackedLoanEmiDayDialog(loan: TrackedLoanSummary, onDismiss: () -> Unit, onConfirm: (dayOfMonth: Int) -> Unit) {
    var dayText by remember { mutableStateOf(loan.emiDayOfMonth.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_txt_monthly_emi_due_day)) },
        text = {
            NumericInputField("Day of Month (1–28)", dayText, { dayText = it.filter { ch -> ch.isDigit() }.take(2) })
        },
        confirmButton = {
            Button(
                onClick = {
                    val day = (dayText.toIntOrNull() ?: loan.emiDayOfMonth).coerceIn(1, 28)
                    onDismiss()
                    onConfirm(day)
                }
            ) { Text(stringResource(R.string.ui_txt_update)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_txt_cancel)) }
        }
    )
}

@Composable
internal fun TrackedLoanDeleteDialog(nickname: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_txt_delete_saved_loan)) },
        text = { Text(stringResource(R.string.ui_txt_remove_and_cancel_any_scheduled_re, nickname)) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) { Text(stringResource(R.string.ui_txt_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_txt_cancel)) }
        }
    )
}
