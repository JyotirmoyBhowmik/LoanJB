package com.jyotirmoyb.loan.features.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.BuildConfig
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.designsystem.FinancialDisclaimerBanner
import com.jyotirmoyb.loan.ui.designsystem.LoanJBSpacing
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme

data class DataSourceCredit(
    val id: String,
    val title: String,
    val authority: String,
    val asOfDate: String,
    val configArtifact: String,
    val scopeDescription: String
)

val OFFICIAL_DATA_SOURCE_CREDITS = listOf(
    DataSourceCredit(
        id = "rbi_monetary",
        title = "RBI Monetary Policy & Retail Lending Directions",
        authority = "Reserve Bank of India (RBI)",
        asOfDate = "As of Q3 FY 2025-26 (Verified 2026-10-15)",
        configArtifact = "rates.json (schema v2)",
        scopeDescription = "Policy Repo Rate (6.50%), SDF/MSF/CRR/SLR, EBLR benchmark defaults, RBI KFS reducing-balance norms, and secured loan LTV caps."
    ),
    DataSourceCredit(
        id = "dea_small_savings",
        title = "Government Small Savings & Post Office Schemes",
        authority = "Dept. of Economic Affairs (MoF) & India Post",
        asOfDate = "As of Q3 FY 2025-26 (Oct–Dec Quarter)",
        configArtifact = "schemes.json (schema v2)",
        scopeDescription = "PPF (7.1%), Sukanya Samriddhi (8.2%), SCSS (8.2%), NSC (7.7%), KVP (7.5%), Post Office MIS/TD/RD, EPF (8.25%), APY, PM-SYM, and PMVVY status."
    ),
    DataSourceCredit(
        id = "cbdt_income_tax",
        title = "Indian Income Tax Act, Slabs, Rebates & TDS Rules",
        authority = "Central Board of Direct Taxes (CBDT) / Income Tax Dept.",
        asOfDate = "As of FY 2025-26 (AY 2026-27) & FY 2024-25",
        configArtifact = "taxslabs.json (schema v2)",
        scopeDescription = "New vs Old Regime tax slabs, Sec 87A rebate & marginal relief, standard deduction (₹75,000 New / ₹50,000 Old), Sec 24(b)/80C/80D/80CCD caps, HRA rules, LTCG/STCG, and Sec 194A FD TDS thresholds."
    ),
    DataSourceCredit(
        id = "gst_council",
        title = "Goods and Services Tax (GST) Rates on Financial Services",
        authority = "GST Council of India",
        asOfDate = "As of FY 2025-26",
        configArtifact = "taxslabs.json & rates.json",
        scopeDescription = "18% standard GST on loan processing fees, foreclosure charges, and credit card EMI interest; CGST/SGST/IGST slab calculator."
    ),
    DataSourceCredit(
        id = "amfi_nav",
        title = "Mutual Fund Daily Scheme NAV Feed",
        authority = "Association of Mutual Funds in India (AMFI)",
        asOfDate = "Live On-Demand / Last Cached NAV Date",
        configArtifact = "AMFI NAVAll.txt (Streamed to local Room DB)",
        scopeDescription = "Public daily Net Asset Value feed parsed via streaming reader for offline scheme search, favourites, and historical CAGR simulations."
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutAndLegalScreen(
    lastCrashDiagnostic: String? = null,
    onClearCrashDiagnostic: (() -> Unit)? = null,
    onOpenDataAndRates: () -> Unit = {},
    onOpenPrivacySecurity: () -> Unit = {}
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "About & Credits",
        "Privacy Policy",
        "Terms & Disclaimers",
        "Play Data Safety"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("about_legal_screen"),
        contentPadding = PaddingValues(
            horizontal = LoanJBSpacing.screenHorizontal,
            vertical = LoanJBSpacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(LoanJBSpacing.md)
    ) {
        item(key = "header_card") {
            FintechCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LoanJBSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LoanJB — Indian Loan & Wealth Planner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • 100% Offline-First",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(LoanJBSpacing.sm))
                FinancialDisclaimerBanner()
            }
        }

        item(key = "section_chips") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(LoanJBSpacing.xs)
            ) {
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("legal_tab_$index")
                    )
                }
            }
        }

        when (selectedSection) {
            0 -> {
                item(key = "about_overview") {
                    FintechCard {
                        Text(
                            text = "Data Sources, Credits & 'As Of' Dates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Every benchmark rate, tax slab, and scheme limit in LoanJB is loaded from versioned JSON configuration files — never hardcoded in formulas. You can inspect or override any rate offline under Data & Rates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(LoanJBSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm)) {
                            OutlinedButton(
                                onClick = onOpenDataAndRates,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("about_open_data_rates_btn")
                            ) {
                                Text("Inspect Data & Rates", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = onOpenPrivacySecurity,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("about_open_security_btn")
                            ) {
                                Text("Security Controls", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                items(
                    items = OFFICIAL_DATA_SOURCE_CREDITS,
                    key = { it.id }
                ) { credit ->
                    FintechCard(modifier = Modifier.testTag("credit_card_${credit.id}")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = credit.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = credit.authority,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "${credit.asOfDate} • Source: ${credit.configArtifact}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = credit.scopeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!lastCrashDiagnostic.isNullOrBlank()) {
                    item(key = "crash_diagnostic_card") {
                        FintechCard {
                            Text(
                                text = "Local Reliability Log (Device-Only)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = lastCrashDiagnostic,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (onClearCrashDiagnostic != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onClearCrashDiagnostic,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Clear Diagnostic Log")
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                item(key = "privacy_policy_text") {
                    FintechCard(modifier = Modifier.testTag("legal_privacy_policy_card")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "In-App Privacy Policy (Effective: November 2026)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        Spacer(Modifier.height(LoanJBSpacing.sm))
                        LegalSectionBlock(
                            heading = "1. Zero Personal Data Collection",
                            body = "LoanJB does not collect, transmit, rent, or sell any personal data, income figures, loan account numbers, tax inputs, or device identifiers. No user account or sign-in is required."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "2. Local-Only Storage & Encryption",
                            body = "All saved loans, EMI payment logs, prepayments, and preferences reside strictly in your device's private sandboxed Room database and DataStore. Optional market API keys and App Lock PIN hashes are encrypted using Android Keystore AES-256-GCM EncryptedSharedPreferences."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "3. Encrypted Backups & Auto-Backup Exclusions",
                            body = "When you export a backup, LoanJB encrypts the JSON archive with your password using PBKDF2-HMAC-SHA256 (120,000 iterations) and AES-256-GCM before sharing. Android Auto Backup rules explicitly exclude the Room database and encrypted preferences from cloud syncing."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "4. Network Requests (GET-Only Public Data)",
                            body = "Network access is strictly optional and used only for HTTP GET requests to fetch public rate configuration files (rates.json, schemes.json, taxslabs.json, articles.json) and AMFI's public NAVAll.txt feed. Zero user financial inputs are ever attached to network requests."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "5. No Analytics, Ads, or Trackers",
                            body = "LoanJB contains zero third-party analytics SDKs, zero crash-reporting telemetry, and zero advertising trackers."
                        )
                    }
                }
            }

            2 -> {
                item(key = "terms_and_disclaimer_text") {
                    FintechCard(modifier = Modifier.testTag("legal_terms_card")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Terms of Use & Financial Advisory Disclaimer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        Spacer(Modifier.height(LoanJBSpacing.sm))
                        LegalSectionBlock(
                            heading = "1. Not a Financial, Investment, or Tax Advisor",
                            body = "LoanJB is an educational and mathematical calculation tool. The app creator is NOT a SEBI-registered Investment Adviser (RIA), RBI-regulated lender, or Chartered Accountant. Nothing in this app constitutes financial, investment, legal, or tax advice."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "2. Accuracy of Bank & Government Rates",
                            body = "Actual bank EMI schedules may vary slightly due to day-count conventions (Actual/365 vs 30/360), broken-period interest, bank-specific rounding rules, or quarterly government rate revisions. Always verify final sanction terms and Key Fact Statements (KFS) with your bank or qualified advisor."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "3. Mutual Fund & Market Risk Warning",
                            body = "Mutual fund investments are subject to market risks. Past CAGR or NAV performance does not guarantee future returns. Asset allocation guides in the app are strictly educational illustrations."
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        LegalSectionBlock(
                            heading = "4. User Responsibility for Encrypted Backups",
                            body = "Because LoanJB operates with zero servers, password-encrypted backups can only be decrypted with the password you set. Keep your backup password safe."
                        )
                    }
                }
            }

            3 -> {
                item(key = "play_data_safety_card") {
                    FintechCard(modifier = Modifier.testTag("legal_data_safety_card")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Google Play Data Safety Declarations",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        Spacer(Modifier.height(LoanJBSpacing.sm))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "• Does this app collect or share any required user data types? NO",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• Data shared with third parties: None (0 data types shared)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• Data collected by the app: None (0 data types collected off-device)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• Local encryption: AES-256-GCM (backups & Android Keystore preferences)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• Data deletion: Instant in-app 'Reset All Local Data' button + full uninstall cleanup",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalSectionBlock(
    heading: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "About & Legal Screen", showBackground = true)
@Composable
private fun AboutAndLegalScreenPreview() {
    LoanJBTheme(themeMode = AppThemeMode.LIGHT) {
        AboutAndLegalScreen()
    }
}
