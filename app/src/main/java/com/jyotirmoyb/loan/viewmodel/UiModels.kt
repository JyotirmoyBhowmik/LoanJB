package com.jyotirmoyb.loan.viewmodel

import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CalculationResult

data class TrackedLoanSummary(
    val entity: SavedLoanEntity,
    val input: CalculationInput,
    val result: CalculationResult,
    val paidMonthNumbers: Set<Int>,
    val completedChecklist: Set<Int>,
    val nextDueMonthNumber: Int?,
    val nextDueDateLabel: String,
    val nextDueYear: Int,
    val nextDueMonth: Int,
    val emiDayOfMonth: Int = 5,
    val autoDebitNach: Boolean = false,
    val outstandingPrincipal: Double,
    val totalPaidSoFar: Double,
    val interestSaved: Double = 0.0,
    val monthsSaved: Int = 0,
    val progressFraction: Float,
    val checklistItems: List<String>
)

data class CompareItemModel(
    val id: Long,
    val label: String,
    val categoryName: String,
    val variantSummary: String,
    val input: CalculationInput,
    val result: CalculationResult
)

data class PortfolioMetrics(
    val totalOutstandingDebt: Double = 0.0,
    val totalMonthlyEmiBurden: Double = 0.0,
    val dtiPercent: Double = 0.0,
    val activeLoanCount: Int = 0,
    val totalInterestSaved: Double = 0.0,
    val totalMonthsSaved: Int = 0
)
