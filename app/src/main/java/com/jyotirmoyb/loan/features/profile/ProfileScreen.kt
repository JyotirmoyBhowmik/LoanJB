package com.jyotirmoyb.loan.features.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.screens.SettingsScreen
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
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
    onOpenSavedLoans: () -> Unit,
    onOpenLoanGuide: () -> Unit,
    onOpenCompare: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Quick Workspace Entry Header inside Profile tab
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(
                        title = "MY LOAN WORKSPACE",
                        badge = "${uiState.savedLoansCount} SAVED",
                        accentColor = TealPrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                ProfileQuickRow(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    tint = TealPrimaryDark,
                    title = stringResource(R.string.hub_saved_loans_title),
                    subtitle = if (uiState.savedLoansCount > 0) "${uiState.savedLoansCount} tracked loan(s) • EMI reminders & DTI" else stringResource(R.string.hub_saved_loans_sub),
                    onClick = onOpenSavedLoans,
                    testTag = "profile_saved_loans_row"
                )
                Spacer(Modifier.height(8.dp))
                ProfileQuickRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    tint = SageGreen,
                    title = stringResource(R.string.hub_loan_guide_title),
                    subtitle = "Explore ${uiState.catalog.categories.size} categories & variant rules",
                    onClick = onOpenLoanGuide,
                    testTag = "profile_loan_guide_row"
                )
                Spacer(Modifier.height(8.dp))
                ProfileQuickRow(
                    icon = Icons.Outlined.CompareArrows,
                    tint = InfoBlue,
                    title = "Loan Comparison",
                    subtitle = "Compare up to 3 bank offers side by side",
                    onClick = onOpenCompare,
                    testTag = "profile_compare_row"
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onReplayOnboarding,
                        modifier = Modifier.testTag("profile_replay_onboarding_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Replay Intro Walkthrough")
                    }
                }
            }
        }

        // Reuse existing untouched SettingsScreen (Currency, Theme, Income, Catalog Editor, Backup/Restore, Reset)
        SettingsScreen(
            preferences = uiState.preferences,
            catalog = uiState.catalog,
            serializeCatalog = serializeCatalog,
            createBackupJson = createBackupJson,
            onSetCurrency = onSetCurrency,
            onSetThemeMode = onSetThemeMode,
            onSetMonthlyIncome = onSetMonthlyIncome,
            onSaveCatalogObject = onSaveCatalogObject,
            onSaveCatalogRawJson = onSaveCatalogRawJson,
            onResetCatalogDefault = onResetCatalogDefault,
            onRestoreBackupJson = onRestoreBackupJson,
            onResetAllData = onResetAllData,
            onShowMessage = onShowMessage,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProfileQuickRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
