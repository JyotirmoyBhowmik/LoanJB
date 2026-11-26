package com.jyotirmoyb.loan.features.mutualfund.xirr

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.mutualfund.MutualFundRiskNoticeBanner
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@Composable
fun XirrScreen(
    uiState: XirrUiState,
    onAddEntry: (date: String, amount: Double, note: String) -> Unit,
    onRemoveEntry: (Long) -> Unit,
    onImportBulk: (String) -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.result
    val currency = uiState.currency
    var dateInput by remember { mutableStateOf("2026-06-15") }
    var amountInput by remember { mutableStateOf("-10000") }
    var noteInput by remember { mutableStateOf("") }
    var showBulkDialog by remember { mutableStateOf(false) }
    var bulkText by remember {
        mutableStateOf(
            "2024-01-10, -10000\n2024-05-10, -15000\n2024-11-10, -25000\n2026-01-10, 62000"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_xirr_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero XIRR Result Card
        item(key = "xirr_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_xirr_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.mf_xirr_annualized),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    if (result.isValid) {
                        Text(
                            text = CurrencyFormatter.formatPercent(result.xirrPercent),
                            style = MonoLargeStyle,
                            color = if (result.xirrPercent >= 0.0) PrincipalEmerald else Color(0xFFFFB4AB)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeroMetricItem(
                                label = stringResource(R.string.mf_total_invested),
                                value = CurrencyFormatter.formatCompact(result.totalInvested, currency),
                                valueColor = Color.White
                            )
                            HeroMetricItem(
                                label = stringResource(R.string.mf_xirr_net_profit),
                                value = CurrencyFormatter.formatCompact(result.netProfit, currency),
                                valueColor = if (result.netProfit >= 0.0) PrincipalEmerald else Color(0xFFFFB4AB)
                            )
                            HeroMetricItem(
                                label = stringResource(R.string.mf_xirr_abs_return),
                                value = CurrencyFormatter.formatPercent(result.absoluteReturnPercent),
                                valueColor = InterestAmber
                            )
                        }
                    } else {
                        Text(
                            text = result.errorMessage ?: "Enter valid dated cash flows",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB4AB)
                        )
                    }
                }
            }
        }

        item(key = "xirr_risk") { MutualFundRiskNoticeBanner() }

        // 2. Add / Paste Cash Flows Card
        item(key = "xirr_add_card") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_xirr_add_row),
                    badge = "${uiState.entries.size} flows",
                    icon = Icons.Outlined.Timeline
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text(stringResource(R.string.mf_xirr_date_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_xirr_date_input")
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { raw ->
                            val cleaned = raw.filter { it.isDigit() || it == '-' || it == '.' }
                            amountInput = cleaned
                        },
                        label = { Text(stringResource(R.string.mf_xirr_amount_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_xirr_amount_input")
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            onAddEntry(dateInput, amt, noteInput)
                            noteInput = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_xirr_add_btn")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                    OutlinedButton(
                        onClick = { showBulkDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_xirr_paste_btn")
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.mf_xirr_bulk_paste))
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onLoadSample,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.mf_xirr_load_sample))
                }
            }
        }

        // 3. Cash Flow List
        items(uiState.entries, key = { it.id }) { entry ->
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.dateIso,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = (if (entry.amount >= 0) "+" else "-") +
                            CurrencyFormatter.format(kotlin.math.abs(entry.amount), currency),
                        style = MonoSmallStyle,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.amount >= 0) SavingsGreen else DangerRed
                    )
                    IconButton(
                        onClick = { onRemoveEntry(entry.id) },
                        modifier = Modifier.testTag("mf_xirr_del_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove cash flow",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "xirr_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showBulkDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            title = { Text(stringResource(R.string.mf_xirr_bulk_paste), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.mf_xirr_paste_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mf_xirr_bulk_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportBulk(bulkText)
                        showBulkDialog = false
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDialog = false }) { Text("Cancel") }
            }
        )
    }
}
