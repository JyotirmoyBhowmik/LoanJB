package com.jyotirmoyb.loan.features.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.market.MarketDashboardSnapshot
import com.jyotirmoyb.loan.data.market.MarketRepository
import com.jyotirmoyb.loan.domain.BankFdRateItem
import com.jyotirmoyb.loan.domain.MarketHistoryPoint
import com.jyotirmoyb.loan.domain.MarketMetricCardData
import com.jyotirmoyb.loan.domain.MarketMetricSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MarketFilterTab(val label: String) {
    ALL("All"),
    RBI("RBI Rates"),
    BULLION("Gold & Silver"),
    FX_INDICES("FX & Indices"),
    BONDS("10Y G-Sec"),
    BANK_FDS("Bank FDs"),
    SMALL_SAVINGS("Small Savings")
}

data class MarketUiState(
    val cards: List<MarketMetricCardData> = emptyList(),
    val bankFdRates: List<BankFdRateItem> = emptyList(),
    val historyByMetric: Map<String, List<MarketHistoryPoint>> = emptyMap(),
    val selectedFilter: MarketFilterTab = MarketFilterTab.ALL,
    val selectedCardKey: String? = null,
    val staleLimitHours: Int = 24,
    val isRefreshing: Boolean = false,
    val lastRefreshMessage: String = "Loaded cached / built-in market rates",
    val configuredApiProvidersCount: Int = 0,
    val snackbarMessage: String? = null
) {
    val selectedCard: MarketMetricCardData?
        get() = selectedCardKey?.let { key -> cards.firstOrNull { it.key == key } }

    val selectedCardHistory: List<MarketHistoryPoint>
        get() = selectedCardKey?.let { historyByMetric[it] }.orEmpty()

    val staleCount: Int
        get() = cards.count { it.isStale }

    fun cardsForSection(section: MarketMetricSection): List<MarketMetricCardData> =
        cards.filter { it.section == section }
}

class MarketViewModel(
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(MarketFilterTab.ALL)
    private val _selectedCardKey = MutableStateFlow<String?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            marketRepository.ensureBaselineCached()
        }
    }

    val uiState: StateFlow<MarketUiState> = combine(
        marketRepository.dashboardFlow,
        _selectedFilter,
        _selectedCardKey,
        _snackbar
    ) { snapshot: MarketDashboardSnapshot, filter, cardKey, snackbar ->
        MarketUiState(
            cards = snapshot.cards,
            bankFdRates = snapshot.bankFdRates,
            historyByMetric = snapshot.historyByMetric,
            selectedFilter = filter,
            selectedCardKey = cardKey,
            staleLimitHours = snapshot.staleLimitHours,
            isRefreshing = snapshot.isRefreshing,
            lastRefreshMessage = snapshot.lastRefreshMessage,
            configuredApiProvidersCount = snapshot.configuredApiProvidersCount,
            snackbarMessage = snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketUiState())

    fun selectFilter(tab: MarketFilterTab) {
        _selectedFilter.value = tab
    }

    fun openMetricDetail(metricKey: String) {
        _selectedCardKey.value = metricKey
    }

    fun closeMetricDetail() {
        _selectedCardKey.value = null
    }

    fun refreshNow() {
        viewModelScope.launch {
            val msg = marketRepository.refreshAll()
            _snackbar.value = msg
        }
    }

    fun saveManualMetric(
        metricKey: String,
        rawText: String,
        onValidationError: (String) -> Unit,
        onSaved: () -> Unit
    ) {
        val parsed = rawText.trim().replace(",", "").toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed < 0.0) {
            onValidationError("Enter a valid non-negative number.")
            return
        }
        viewModelScope.launch {
            val res = marketRepository.saveManualMetric(metricKey, parsed)
            res.fold(
                onSuccess = {
                    _snackbar.value = "Manual value saved & added to history"
                    onSaved()
                },
                onFailure = { err ->
                    onValidationError(err.message ?: "Could not save value")
                }
            )
        }
    }

    fun resetManualMetric(metricKey: String) {
        viewModelScope.launch {
            marketRepository.resetManualMetric(metricKey)
            _snackbar.value = "Reset to default market source"
        }
    }

    fun saveBankFdRate(
        bankId: String?,
        bankName: String,
        generalRateText: String,
        seniorRateText: String,
        tenureLabel: String,
        onValidationError: (String) -> Unit,
        onSaved: () -> Unit
    ) {
        val gen = generalRateText.trim().toDoubleOrNull()
        val sen = seniorRateText.trim().toDoubleOrNull()
        if (bankName.isBlank()) {
            onValidationError("Enter bank name.")
            return
        }
        if (gen == null || sen == null || gen !in 0.0..25.0 || sen !in 0.0..25.0) {
            onValidationError("Enter valid FD rates between 0% and 25%.")
            return
        }
        viewModelScope.launch {
            val res = marketRepository.saveBankFdRate(
                bankId = bankId,
                bankName = bankName,
                generalRate = gen,
                seniorRate = sen,
                tenureLabel = tenureLabel
            )
            res.fold(
                onSuccess = {
                    _snackbar.value = "Saved FD rate for ${bankName.trim()}"
                    onSaved()
                },
                onFailure = { err ->
                    onValidationError(err.message ?: "Could not save bank FD rate")
                }
            )
        }
    }

    fun deleteBankFdRate(bankId: String, bankName: String) {
        viewModelScope.launch {
            marketRepository.deleteBankFdRate(bankId)
            _snackbar.value = "Removed custom rate for $bankName"
        }
    }

    fun resetAllBankFdRates() {
        viewModelScope.launch {
            marketRepository.resetAllBankFdRates()
            _snackbar.value = "Reset bank FD table to built-in defaults"
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    class Factory(private val marketRepository: MarketRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MarketViewModel(marketRepository) as T
        }
    }
}
