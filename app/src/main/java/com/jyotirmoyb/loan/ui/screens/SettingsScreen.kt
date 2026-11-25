package com.jyotirmoyb.loan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.util.ScheduleExporter
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    catalog: LoanCatalog,
    serializeCatalog: (LoanCatalog) -> String,
    createBackupJson: suspend () -> String,
    onSetCurrency: (CurrencyCode) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetMonthlyIncome: (Double) -> Unit,
    onSaveCatalogObject: (LoanCatalog) -> Unit,
    onSaveCatalogRawJson: (String) -> Unit,
    onResetCatalogDefault: () -> Unit,
    onRestoreBackupJson: (String) -> Unit,
    onResetAllData: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCatalogEditor by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var incomeText by remember(preferences.monthlyIncome) {
        mutableStateOf(if (preferences.monthlyIncome == 0.0) "" else preferences.monthlyIncome.toLong().toString())
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { json ->
                if (!json.isNullOrBlank()) {
                    onRestoreBackupJson(json)
                } else {
                    onShowMessage("Selected file was empty.")
                }
            }.onFailure {
                onShowMessage("Could not read backup file.")
            }
        }
    }

    if (showCatalogEditor) {
        CatalogEditorScreen(
            catalog = catalog,
            catalogJson = remember(catalog) { serializeCatalog(catalog) },
            onBack = { showCatalogEditor = false },
            onSaveCatalogObject = { updated ->
                onSaveCatalogObject(updated)
                showCatalogEditor = false
            },
            onSaveCatalogRawJson = { raw ->
                onSaveCatalogRawJson(raw)
                showCatalogEditor = false
            },
            onResetDefault = onResetCatalogDefault,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Currency
        item {
            FintechCard {
                SectionAccentHeader("Currency & Formatting", badge = preferences.currency.code)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyCode.entries.forEach { code ->
                        FilterChip(
                            selected = preferences.currency == code,
                            onClick = { onSetCurrency(code) },
                            label = { Text(if (code == CurrencyCode.INR) "INR ₹ (12,34,567)" else "USD $ (1,234,567)") },
                            modifier = Modifier.testTag("settings_currency_${code.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Appearance
        item {
            FintechCard {
                SectionAccentHeader("Appearance", badge = preferences.themeMode.name)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Monthly Income (DTI)
        item {
            FintechCard {
                SectionAccentHeader("Monthly Net Income", badge = "FOR DTI")
                Spacer(Modifier.height(10.dp))
                NumericInputField(
                    label = "Monthly Take-Home Income (${preferences.currency.symbol})",
                    valueText = incomeText,
                    onValueChange = { txt ->
                        incomeText = txt
                        txt.toDoubleOrNull()?.let { onSetMonthlyIncome(it) }
                    },
                    prefix = "${preferences.currency.symbol} ",
                    trailingBadge = CurrencyFormatter.formatCompact(preferences.monthlyIncome, preferences.currency),
                    supportingText = "Used to calculate your Debt-to-Income (DTI) ratio in My Loans.",
                    modifier = Modifier.testTag("settings_income_input")
                )
            }
        }

        // Editable Loan Catalog
        item {
            FintechCard {
                SectionAccentHeader("Loan Catalog Config", badge = "${catalog.categories.size} CATS")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Customize rate ranges, tenure limits, Nepal Rastra Bank LTV caps (EV vs Fuel), or add your own categories and variants.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showCatalogEditor = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_edit_catalog_btn")
                ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Loan Catalog")
                }
            }
        }

        // Offline Backup & Restore JSON
        item {
            FintechCard {
                SectionAccentHeader("Offline Backup & Restore", badge = "JSON")
                Spacer(Modifier.height(6.dp))
                Text(
                    "LoanJB stores 100% of your data locally with zero login. Export a JSON backup anytime or restore from a file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = createBackupJson()
                                val res = ScheduleExporter.exportBackupJsonAndShare(context, json)
                                if (res.isFailure) onShowMessage("Backup export failed")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_backup_json_btn")
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Backup JSON")
                    }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_restore_json_btn")
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore JSON")
                    }
                }
            }
        }

        // Danger Zone: Reset All Data
        item {
            FintechCard(borderColor = DangerRed.copy(alpha = 0.35f)) {
                SectionAccentHeader("Reset Local Data", accentColor = DangerRed)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Clears saved loans, comparison scenarios, preferences, and restores the default seed catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_all_btn")
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset All Data")
                }
            }
        }

        item {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset All Data?") },
            text = { Text("This will delete all saved loans, comparison scenarios, and custom catalog edits on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Reset Everything", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}
