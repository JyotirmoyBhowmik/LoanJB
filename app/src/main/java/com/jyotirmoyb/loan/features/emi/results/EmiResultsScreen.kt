package com.jyotirmoyb.loan.features.emi.results

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.features.emi.EmiUiState
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MintTopStartDark
import com.jyotirmoyb.loan.ui.theme.MintTopStartLight
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimary
import com.jyotirmoyb.loan.util.ScheduleExporter

@Composable
fun EmiResultsScreen(
    uiState: EmiUiState,
    shareSummaryText: () -> String,
    onBack: () -> Unit,
    onSaveLoan: (String, String, Boolean, Int) -> Unit,
    onAddToCompare: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val result = uiState.result
    val currency = uiState.currency
    val isDark = isSystemInDarkTheme()

    // 0 = Yearly, 1 = Monthly (default matching screenshot 2), 2 = Graph
    var selectedTab by remember { mutableIntStateOf(1) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val topGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(MintTopStartDark, MaterialTheme.colorScheme.background)
        } else {
            listOf(MintTopStartLight, MaterialTheme.colorScheme.background)
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("emi_results_screen")
    ) {
        // Mint-to-white header + tabs + KPI strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(topGradient)
        ) {
            // Header row: Back + "Loan (EMI)" + Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("results_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = uiState.selectedCategory?.let { "${it.shortName} (EMI)" }
                        ?: stringResource(R.string.emi_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareSummaryText())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Share Loan Summary"))
                        }.onFailure {
                            onShowMessage("Could not open share sheet")
                        }
                    },
                    modifier = Modifier.testTag("results_share_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.results_share),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 3 Tabs: Yearly | Monthly | Graph
            val tabs = listOf(
                stringResource(R.string.results_tab_yearly),
                stringResource(R.string.results_tab_monthly),
                stringResource(R.string.results_tab_graph)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { selectedTab = idx }
                            .testTag("results_tab_${label.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(3.dp)
                                .background(
                                    if (active) TealPrimary else Color.Transparent,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // MoneyWink 3-Column Summary Strip Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("results_summary_strip"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStripColumn(
                            label = stringResource(R.string.results_principal),
                            value = CurrencyFormatter.format(result.totalPrincipal, currency),
                            valueColor = PrincipalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStripColumn(
                            label = stringResource(R.string.results_total_interest),
                            value = CurrencyFormatter.format(result.totalInterest, currency),
                            valueColor = InterestAmber,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStripColumn(
                            label = stringResource(R.string.results_total_payment),
                            value = CurrencyFormatter.format(result.totalPayment, currency),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // EMI + Prepayment highlight chip row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "EMI: ${CurrencyFormatter.format(result.monthlyEmi, currency, true)}/mo • ${result.actualTenureMonths}m",
                                style = MonoSmallStyle,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        result.insights.prepaymentImpact?.let { prep ->
                            Surface(
                                color = SavingsGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Saved ${CurrencyFormatter.formatCompact(prep.interestSaved, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Quick Actions Row: Save Loan | Compare | PDF | CSV
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { showSaveDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("results_save_loan_btn")
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save_loan), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAddToCompare,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("results_compare_btn")
                ) {
                    Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_to_compare), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val title = uiState.selectedCategory?.name ?: "Loan (EMI)"
                        val res = ScheduleExporter.exportPdfAndShare(context, title, uiState.variantSummary, result, currency)
                        if (res.isFailure) onShowMessage("Could not export PDF")
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("results_export_pdf_btn")
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = stringResource(R.string.export_pdf), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("PDF", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        val title = uiState.selectedCategory?.name ?: "Loan (EMI)"
                        val res = ScheduleExporter.exportCsvAndShare(context, title, result, currency)
                        if (res.isFailure) onShowMessage("Could not export CSV")
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("results_export_csv_btn")
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = stringResource(R.string.export_csv), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("CSV", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> YearlyScheduleTab(result = result, currency = currency)
                1 -> MonthlyScheduleTab(result = result, currency = currency)
                2 -> GraphTab(input = uiState.input, result = result, currency = currency)
            }
        }
    }

    if (showSaveDialog) {
        SaveLoanDialog(
            defaultNickname = "${uiState.selectedCategory?.shortName ?: "Loan"} Plan",
            onDismiss = { showSaveDialog = false },
            onConfirm = { nickname, lender, reminder, daysBefore ->
                showSaveDialog = false
                onSaveLoan(nickname, lender, reminder, daysBefore)
            }
        )
    }
}

@Composable
private fun SummaryStripColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MonoMediumStyle,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun SaveLoanDialog(
    defaultNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Int) -> Unit
) {
    var nickname by remember { mutableStateOf(defaultNickname) }
    var lender by remember { mutableStateOf("SBI / HDFC / ICICI") }
    var reminderEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Saved Loans", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Loan Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lender,
                    onValueChange = { lender = it },
                    label = { Text("Bank / Lender") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { reminderEnabled = !reminderEnabled }
                ) {
                    Checkbox(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    Text("Remind 3 days before EMI due date", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nickname, lender, reminderEnabled, 3) },
                modifier = Modifier.testTag("save_loan_confirm_btn")
            ) {
                Text("Save Loan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
