package com.jyotirmoyb.loan.viewmodel

import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult

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
    val outstandingPrincipal: Double,
    val totalPaidSoFar: Double,
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
    val activeLoanCount: Int = 0
)
