package com.jyotirmoyb.loan.features.mutualfund.nav

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.AmfiNavEntity
import com.jyotirmoyb.loan.features.mutualfund.MutualFundRiskNoticeBanner
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

@Composable
fun LiveNavScreen(
    uiState: LiveNavUiState,
    onSearchQueryChange: (String) -> Unit,
    onSetFavouritesOnly: (Boolean) -> Unit,
    onToggleFavourite: (AmfiNavEntity) -> Unit,
    onSyncNow: () -> Unit,
    onCalculateWithFund: (fundName: String, navInfo: String, cagrPercent: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFundForCalc by remember { mutableStateOf<AmfiNavEntity?>(null) }
    var cagrInputText by remember { mutableStateOf("12.0") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_live_nav_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Offline / Sync Status Banner
        item(key = "nav_sync_banner") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.totalCachedCount > 0) Icons.Outlined.CloudDownload else Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = if (uiState.totalCachedCount > 0) SavingsGreen else InterestAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (uiState.totalCachedCount > 0) {
                                    "${uiState.totalCachedCount} AMFI Schemes Cached"
                                } else {
                                    "Offline — No Cached NAVs"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        val statusText = uiState.syncMeta?.let { meta ->
                            if (meta.latestNavDate.isNotBlank()) {
                                "Latest NAV Date: ${meta.latestNavDate} • ${meta.statusMessage}"
                            } else {
                                meta.statusMessage
                            }
                        } ?: "Streams official AMFI NAVAll.txt directly to local Room cache."
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onSyncNow,
                        enabled = !uiState.isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("mf_nav_sync_btn")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.isSyncing) "Syncing" else "Sync")
                    }
                }
            }
        }

        // 2. Search Input & All / Favourites Segmented Control
        item(key = "nav_search_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.mf_nav_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mf_nav_search_input")
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !uiState.showFavouritesOnly,
                        onClick = { onSetFavouritesOnly(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("mf_nav_tab_all")
                    ) {
                        Text(stringResource(R.string.mf_nav_tab_all))
                    }
                    SegmentedButton(
                        selected = uiState.showFavouritesOnly,
                        onClick = { onSetFavouritesOnly(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("mf_nav_tab_favs")
                    ) {
                        Text("${stringResource(R.string.mf_nav_tab_favs)} (${uiState.favouritesCount})")
                    }
                }
            }
        }

        item(key = "nav_risk") { MutualFundRiskNoticeBanner() }

        // 3. Scheme Cards or Empty State
        if (uiState.schemes.isEmpty()) {
            item(key = "nav_empty") {
                FintechCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (uiState.showFavouritesOnly) "No favourite schemes yet"
                            else stringResource(R.string.mf_nav_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.showFavouritesOnly) "Tap the star icon on any fund to bookmark it for offline access."
                            else stringResource(R.string.mf_nav_empty_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.schemes, key = { it.schemeCode }) { scheme ->
                AmfiSchemeCard(
                    scheme = scheme,
                    onToggleFavourite = { onToggleFavourite(scheme) },
                    onClick = {
                        cagrInputText = "12.0"
                        selectedFundForCalc = scheme
                    }
                )
            }
        }

        item(key = "nav_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }

    // "Calculate SIP with this fund's historical / expected CAGR" dialog
    selectedFundForCalc?.let { fund ->
        AlertDialog(
            onDismissRequest = { selectedFundForCalc = null },
            title = {
                Text(
                    text = stringResource(R.string.mf_nav_calc_cta),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = fund.schemeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "NAV: ₹${"%.4f".format(fund.nav)} (as of ${fund.navDate}) • Code ${fund.schemeCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NumericInputField(
                        label = stringResource(R.string.mf_nav_cagr_label),
                        valueText = cagrInputText,
                        onValueChange = { cagrInputText = it },
                        trailingBadge = "% p.a.",
                        modifier = Modifier.testTag("mf_nav_cagr_dialog_input")
                    )
                    MutualFundRiskNoticeBanner()
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cagr = cagrInputText.toDoubleOrNull()?.coerceIn(0.5, 45.0) ?: 12.0
                        val navInfo = "NAV ₹${"%.2f".format(fund.nav)} as of ${fund.navDate}"
                        selectedFundForCalc = null
                        onCalculateWithFund(fund.schemeName, navInfo, cagr)
                    },
                    modifier = Modifier.testTag("mf_nav_open_sip_btn")
                ) {
                    Text("Open SIP Calculator")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedFundForCalc = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AmfiSchemeCard(
    scheme: AmfiNavEntity,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("mf_nav_item_${scheme.schemeCode}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scheme.schemeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                val metaLine = buildString {
                    append("Code ${scheme.schemeCode}")
                    if (scheme.isinGrowth.isNotBlank()) append(" • ${scheme.isinGrowth}")
                }
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (scheme.amcName.isNotBlank()) {
                    Text(
                        text = scheme.amcName,
                        style = MaterialTheme.typography.labelSmall,
                        color = InfoBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${"%.2f".format(scheme.nav)}",
                    style = MonoMediumStyle,
                    fontWeight = FontWeight.Bold,
                    color = SageGreen
                )
                Text(
                    text = scheme.navDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onToggleFavourite,
                modifier = Modifier.testTag("mf_nav_fav_${scheme.schemeCode}")
            ) {
                Icon(
                    imageVector = if (scheme.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Toggle favourite",
                    tint = if (scheme.isFavourite) InterestAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
