package com.jyotirmoyb.loan.features.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.domain.BankFdRateItem
import com.jyotirmoyb.loan.domain.MarketMetricCardData
import com.jyotirmoyb.loan.domain.MarketMetricSection
import com.jyotirmoyb.loan.domain.MarketSourceType
import com.jyotirmoyb.loan.domain.MarketStalenessEvaluator
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.FeesIndigo
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    uiState: MarketUiState,
    onSelectFilter: (MarketFilterTab) -> Unit,
    onOpenMetricDetail: (String) -> Unit,
    onCloseMetricDetail: () -> Unit,
    onRefreshNow: () -> Unit,
    onSaveManualMetric: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    onResetManualMetric: (String) -> Unit,
    onSaveBankFd: (String?, String, String, String, String, (String) -> Unit, () -> Unit) -> Unit,
    onDeleteBankFd: (String, String) -> Unit,
    onResetAllBankFds: () -> Unit,
    onOpenDataAndRates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBankFd by remember { mutableStateOf<BankFdRateItem?>(null) }
    var addingNewBankFd by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefreshNow,
        modifier = modifier
            .fillMaxSize()
            .testTag("market_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Market Overview Header Card
            item {
                FintechCard {
                    SectionAccentHeader(
                        title = stringResource(R.string.market_title),
                        badge = if (uiState.configuredApiProvidersCount > 0) {
                            "${uiState.configuredApiProvidersCount} LIVE API"
                        } else {
                            "REMOTE + MANUAL"
                        },
                        accentColor = TealPrimaryDark,
                        icon = Icons.AutoMirrored.Outlined.ShowChart
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiState.lastRefreshMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ui_txt_stale_alert_after_h, uiState.staleLimitHours),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.staleCount > 0) {
                            MarketStaleBadge()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRefreshNow,
                            enabled = !uiState.isRefreshing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("market_refresh_btn")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isRefreshing) {
                                    stringResource(
                                        R.string.syncing_now
                                    )
                                } else {
                                    stringResource(R.string.refresh_now)
                                },
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenDataAndRates,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("market_api_settings_btn")
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.market_api_providers_short), maxLines = 1)
                        }
                    }
                }
            }

            // Section Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarketFilterTab.entries.forEach { tab ->
                        FilterChip(
                            selected = uiState.selectedFilter == tab,
                            onClick = { onSelectFilter(tab) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("market_filter_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // 1. RBI Policy & Benchmark Rates
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.RBI) {
                item {
                    MarketSectionBlock(
                        title = MarketMetricSection.RBI_BENCHMARKS.title,
                        icon = Icons.Outlined.AccountBalance,
                        accentColor = TealPrimaryDark,
                        cards = uiState.cardsForSection(MarketMetricSection.RBI_BENCHMARKS),
                        onCardClick = onOpenMetricDetail
                    )
                }
            }

            // 2. Gold & Silver (Bullion)
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.BULLION) {
                item {
                    MarketSectionBlock(
                        title = MarketMetricSection.BULLION.title,
                        icon = Icons.Outlined.CurrencyRupee,
                        accentColor = InterestAmber,
                        cards = uiState.cardsForSection(MarketMetricSection.BULLION),
                        onCardClick = onOpenMetricDetail
                    )
                }
            }

            // 3. USD-INR & Equity Indices
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.FX_INDICES) {
                item {
                    MarketSectionBlock(
                        title = MarketMetricSection.FX_INDICES.title,
                        icon = Icons.Outlined.Timeline,
                        accentColor = FeesIndigo,
                        cards = uiState.cardsForSection(MarketMetricSection.FX_INDICES),
                        onCardClick = onOpenMetricDetail
                    )
                }
            }

            // 4. India 10-Year G-Sec Yield
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.BONDS) {
                item {
                    MarketSectionBlock(
                        title = MarketMetricSection.BONDS.title,
                        icon = Icons.AutoMirrored.Outlined.ShowChart,
                        accentColor = PurpleAccent,
                        cards = uiState.cardsForSection(MarketMetricSection.BONDS),
                        onCardClick = onOpenMetricDetail
                    )
                }
            }

            // 5. Top Bank FD Rates (Editable Table)
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.BANK_FDS) {
                item {
                    EditableBankFdTableCard(
                        rows = uiState.bankFdRates,
                        onAddClick = { addingNewBankFd = true },
                        onEditClick = { editingBankFd = it },
                        onDeleteClick = { onDeleteBankFd(it.bankId, it.bankName) },
                        onResetAll = onResetAllBankFds
                    )
                }
            }

            // 6. Current Small-Savings Rates
            if (uiState.selectedFilter == MarketFilterTab.ALL || uiState.selectedFilter == MarketFilterTab.SMALL_SAVINGS) {
                item {
                    MarketSectionBlock(
                        title = MarketMetricSection.SMALL_SAVINGS.title,
                        icon = Icons.Outlined.Savings,
                        accentColor = SageGreenDark,
                        cards = uiState.cardsForSection(MarketMetricSection.SMALL_SAVINGS),
                        onCardClick = onOpenMetricDetail,
                        footnote = stringResource(R.string.market_small_savings_quarterly_note)
                    )
                }
            }

            item {
                DisclaimerBanner()
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    uiState.selectedCard?.let { activeCard ->
        MarketMetricDetailSheet(
            card = activeCard,
            history = uiState.selectedCardHistory,
            staleLimitHours = uiState.staleLimitHours,
            onDismiss = onCloseMetricDetail,
            onSaveManual = { text, onErr, onDone ->
                onSaveManualMetric(activeCard.key, text, onErr, onDone)
            },
            onResetManual = { onResetManualMetric(activeCard.key) }
        )
    }

    if (addingNewBankFd) {
        EditBankFdDialog(
            initial = null,
            onDismiss = { addingNewBankFd = false },
            onSave = onSaveBankFd
        )
    }

    editingBankFd?.let { target ->
        EditBankFdDialog(
            initial = target,
            onDismiss = { editingBankFd = null },
            onSave = onSaveBankFd
        )
    }
}

@Composable
private fun MarketSectionBlock(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    cards: List<MarketMetricCardData>,
    onCardClick: (String) -> Unit,
    footnote: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionAccentHeader(
            title = title,
            badge = "${cards.size}",
            accentColor = accentColor,
            icon = icon
        )
        cards.forEach { card ->
            MarketMetricCard(
                card = card,
                accentColor = accentColor,
                onClick = { onCardClick(card.key) }
            )
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun MarketMetricCard(card: MarketMetricCardData, accentColor: Color, onClick: () -> Unit) {
    FintechCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("market_card_${card.key}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MarketStalenessEvaluator.formatMetricValue(card.value, card.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
                Spacer(Modifier.height(2.dp))
                MarketDeltaChip(card.deltaAbs, card.deltaPercent, card.unit)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mappedSource = when (card.sourceType) {
                MarketSourceType.MANUAL -> ConfigValueSource.MANUAL
                MarketSourceType.IMPORTED -> ConfigValueSource.IMPORTED
                MarketSourceType.ONLINE, MarketSourceType.LIVE_API -> ConfigValueSource.ONLINE
                MarketSourceType.BUILT_IN -> ConfigValueSource.BUILT_IN
            }
            val compactDate = DataSourceResolver.formatMillisToCompactDate(card.asOfMillis)
            ValueSourceBadgesRow(
                source = mappedSource,
                asOfDate = compactDate,
                isUnverified = card.isUnverified,
                sourceName = card.sourceName,
                sourceUrl = card.sourceUrl,
                effectiveFrom = card.effectiveFrom.ifBlank { compactDate },
                lastVerified = card.lastVerified,
                customSourceLabel = if (card.sourceType == MarketSourceType.LIVE_API) {
                    stringResource(R.string.source_live_api)
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
            if (card.isStale) {
                MarketStaleBadge()
            }
        }
    }
}

@Composable
private fun EditableBankFdTableCard(
    rows: List<BankFdRateItem>,
    onAddClick: () -> Unit,
    onEditClick: (BankFdRateItem) -> Unit,
    onDeleteClick: (BankFdRateItem) -> Unit,
    onResetAll: () -> Unit
) {
    val hasManualEdits = rows.any { it.isManualOverride }

    FintechCard(modifier = Modifier.testTag("market_bank_fd_table")) {
        SectionAccentHeader(
            title = stringResource(R.string.market_top_fd_rates_title),
            badge = "EDITABLE",
            accentColor = SavingsGreen,
            icon = Icons.Outlined.AccountBalance
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.market_top_fd_rates_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_txt_bank_tenure),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = stringResource(R.string.ui_txt_gen),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.65f)
            )
            Text(
                text = stringResource(R.string.ui_txt_sr),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.65f)
            )
            Spacer(Modifier.width(44.dp))
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        rows.forEach { bank ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.5f).padding(end = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = bank.bankName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (bank.isManualOverride) {
                            Surface(
                                color = PurpleAccent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_txt_edited),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PurpleAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = bank.tenureLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format("%.2f%%", bank.generalRate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimaryDark,
                    modifier = Modifier.weight(0.65f)
                )
                Text(
                    text = String.format("%.2f%%", bank.seniorRate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SavingsGreen,
                    modifier = Modifier.weight(0.65f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onEditClick(bank) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_bank_fd_${bank.bankId}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${bank.bankName}",
                            tint = TealPrimaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (bank.bankId.startsWith("custom_")) {
                        IconButton(
                            onClick = { onDeleteClick(bank) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete ${bank.bankName}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("market_add_bank_fd_btn")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.market_add_bank_fd))
            }
            if (hasManualEdits) {
                TextButton(
                    onClick = onResetAll,
                    modifier = Modifier.testTag("market_reset_bank_fds_btn")
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reset_default))
                }
            }
        }
    }
}
