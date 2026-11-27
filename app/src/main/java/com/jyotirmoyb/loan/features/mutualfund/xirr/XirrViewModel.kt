package com.jyotirmoyb.loan.features.mutualfund.xirr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.domain.CashFlowEntry
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.XirrEngine
import com.jyotirmoyb.loan.domain.XirrResult
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class XirrUiState(
    val entries: List<CashFlowEntry> = sampleXirrEntries(),
    val result: XirrResult = XirrEngine.calculate(sampleXirrEntries()),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

private fun sampleXirrEntries(): List<CashFlowEntry> = listOf(
    CashFlowEntry(id = 1, dateIso = "2024-01-10", amount = -15000.0, note = "SIP Jan"),
    CashFlowEntry(id = 2, dateIso = "2024-04-10", amount = -15000.0, note = "SIP Apr"),
    CashFlowEntry(id = 3, dateIso = "2024-08-15", amount = -20000.0, note = "Lumpsum dip"),
    CashFlowEntry(id = 4, dateIso = "2025-01-10", amount = -15000.0, note = "SIP Jan"),
    CashFlowEntry(id = 5, dateIso = "2026-01-10", amount = 82500.0, note = "Current Value")
)

class XirrViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<List<CashFlowEntry>, XirrResult> {

    override val id: String = "mf_xirr"

    private val repository = AppContainer.provideRepository(application)
    private val _entries = MutableStateFlow(sampleXirrEntries())
    private val _snackbar = MutableStateFlow<String?>(null)
    private var nextId = 100L

    val uiState: StateFlow<XirrUiState> = combine(
        _entries,
        repository.preferencesFlow,
        _snackbar
    ) { list, prefs: UserPreferences, msg ->
        XirrUiState(
            entries = list,
            result = compute(list),
            currency = prefs.currency,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XirrUiState())

    override fun compute(input: List<CashFlowEntry>): XirrResult = XirrEngine.calculate(input)

    fun addEntry(dateRaw: String, amount: Double, note: String) {
        val iso = XirrEngine.normalizeDateToIso(dateRaw)
        if (iso == null) {
            _snackbar.value = "Invalid date. Use YYYY-MM-DD (e.g., 2025-04-10)."
            return
        }
        if (amount == 0.0) {
            _snackbar.value = "Amount cannot be 0."
            return
        }
        _entries.value = (_entries.value + CashFlowEntry(nextId++, iso, amount, note.trim()))
            .sortedBy { it.dateIso }
    }

    fun removeEntry(id: Long) {
        _entries.value = _entries.value.filterNot { it.id == id }
    }

    fun importBulkText(rawText: String) {
        val parsed = XirrEngine.parseBulkCashFlows(rawText)
        if (parsed.size < 2) {
            _snackbar.value = "Could not parse at least 2 valid lines (format: YYYY-MM-DD, -10000)."
            return
        }
        _entries.value = parsed.map { it.copy(id = nextId++) }.sortedBy { it.dateIso }
        _snackbar.value = "Imported ${parsed.size} dated cash flows."
    }

    fun loadSample() {
        _entries.value = sampleXirrEntries()
        _snackbar.value = "Loaded sample irregular SIP cash flows."
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}
