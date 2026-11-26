package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.viewmodel.PortfolioMetrics
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary
import kotlin.math.max

data class NextEmiWidgetData(
    val hasActiveLoan: Boolean,
    val loanId: Long = 0L,
    val nickname: String = "",
    val lender: String = "",
    val emiAmountFormatted: String = "",
    val dueDateLabel: String = "",
    val emiDayOfMonth: Int = 5,
    val autoDebitNach: Boolean = false,
    val activeLoanCount: Int = 0,
    val totalMonthlyBurdenFormatted: String = ""
)

/**
 * Pure domain engine for My Loans tracking, schedule recomputation after logged
 * prepayments and floating-rate resets, interest saved calculations, Indian closure
 * checklists (NOC, lien/CERSAI release, CIBIL check, Form 35 for vehicles), and
 * next-due widget selection.
 */
object LoanTrackerEngine {

    private val STANDARD_INDIA_CLOSURE_STEPS = listOf(
        "Collect Bank Loan Closure Letter & No-Dues Certificate (NOC)",
        "Release Collateral Lien / MODT / CERSAI Charge & collect original documents",
        "Verify 'Closed' status & ₹0 balance on CIBIL / Experian / Equifax report (30–45 days)",
        "Cancel active NACH / ECS auto-debit mandate & retrieve unused security cheques"
    )

    private val VEHICLE_FORM35_STEPS = listOf(
        "Obtain 2 bank-signed copies of RTO Form 35 + Hypothecation Termination NOC",
        "Submit Form 35 on Parivahan / Regional RTO & collect updated RC without hypothecation"
    )

    fun applyPrepayment(
        input: CalculationInput,
        monthNumber: Int,
        amount: Double,
        reduceTenure: Boolean
    ): CalculationInput {
        if (amount <= 0.0) return input
        val rule = PrepaymentRule(
            startMonth = monthNumber.coerceAtLeast(1),
            amount = amount,
            isRecurring = false,
            mode = if (reduceTenure) PrepaymentMode.REDUCE_TENURE.name else PrepaymentMode.REDUCE_EMI.name
        )
        return input.copy(
            prepaymentEnabled = true,
            prepayments = input.prepayments + rule
        )
    }

    fun applyRateChange(
        input: CalculationInput,
        afterMonth: Int,
        newAnnualRate: Double
    ): CalculationInput {
        val clampedRate = newAnnualRate.coerceIn(0.0, 40.0)
        val clampedMonth = afterMonth.coerceAtLeast(0)
        val flags = input.ruleFlags.toMutableList()
        if (!flags.contains("RATE_FLOATING") && !flags.contains("RATE_HYBRID") && !flags.contains("RATE_EBLR") && !flags.contains("RATE_MCLR")) {
            flags.add("RATE_FLOATING")
        }
        val updatedEvents = (input.rateChanges.filterNot { it.afterMonth == clampedMonth } +
            RateChangeEvent(afterMonth = clampedMonth, newAnnualRate = clampedRate))
            .sortedBy { it.afterMonth }
        return input.copy(
            ruleFlags = flags,
            rateChanges = updatedEvents
        )
    }

    fun isVehicleLoan(categoryId: String, categoryName: String = ""): Boolean {
        val combined = "$categoryId $categoryName".lowercase()
        return combined.contains("auto") ||
            combined.contains("car") ||
            combined.contains("vehicle") ||
            combined.contains("two_wheeler") ||
            combined.contains("two-wheeler") ||
            combined.contains("bike") ||
            combined.contains("ev")
    }

    fun buildClosureChecklist(
        categoryId: String,
        categoryName: String = "",
        categoryChecklistItems: List<String> = emptyList()
    ): List<String> {
        val result = LinkedHashSet<String>()
        result.addAll(STANDARD_INDIA_CLOSURE_STEPS)
        if (isVehicleLoan(categoryId, categoryName)) {
            result.addAll(VEHICLE_FORM35_STEPS)
        }
        categoryChecklistItems.forEach { item ->
            val trimmed = item.trim()
            if (trimmed.isNotEmpty() && result.none { it.equals(trimmed, ignoreCase = true) }) {
                // Avoid near-duplicate Form 35 / NOC lines if catalog already has a variant
                val lower = trimmed.lowercase()
                val alreadyHasForm35 = lower.contains("form 35") && result.any { it.lowercase().contains("form 35") }
                val alreadyHasCibil = lower.contains("cibil") && result.any { it.lowercase().contains("cibil") }
                if (!alreadyHasForm35 && !alreadyHasCibil) {
                    result.add(trimmed)
                }
            }
        }
        return result.toList()
    }

    fun evaluateTrackedLoan(
        entity: SavedLoanEntity,
        input: CalculationInput,
        category: LoanCategory? = null
    ): TrackedLoanSummary {
        val effectiveEmiDay = entity.emiDayOfMonth.coerceIn(1, 28)
        val activeInput = input.copy(emiDayOfMonth = effectiveEmiDay)
        val result = LoanCalculationEngine.calculate(activeInput, category)

        // Baseline schedule without prepayments or floating rate resets to measure interest & months saved
        val hasAdjustments = activeInput.prepaymentEnabled && activeInput.prepayments.isNotEmpty() ||
            activeInput.rateChanges.isNotEmpty()
        val baselineResult = if (hasAdjustments) {
            LoanCalculationEngine.calculate(
                activeInput.copy(
                    prepaymentEnabled = false,
                    prepayments = emptyList(),
                    rateChanges = emptyList()
                ),
                category
            )
        } else {
            result
        }

        val interestSaved = LoanCalculationEngine.roundPaisa(
            max(0.0, baselineResult.totalInterest - result.totalInterest)
        )
        val monthsSaved = max(0, baselineResult.actualTenureMonths - result.actualTenureMonths)

        val paidSet = entity.paidMonthsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..result.schedule.size }
            .toSet()
        val checklistSet = entity.completedChecklistCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        val paidRows = result.schedule.filter { paidSet.contains(it.monthNumber) }
        val paidPrincipal = paidRows.sumOf { it.principalPaid + it.extraPrepayment }
        val totalPaidSoFar = paidRows.sumOf { it.emiPaid + it.extraPrepayment }
        val outstanding = LoanCalculationEngine.roundPaisa(max(0.0, result.totalPrincipal - paidPrincipal))
        val progress = if (result.schedule.isNotEmpty()) {
            (paidSet.size.toFloat() / result.schedule.size.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val nextRow = result.schedule.firstOrNull { !paidSet.contains(it.monthNumber) }
        val nextNum = nextRow?.monthNumber
        val nextOffset = (nextNum ?: 1) - 1
        val totalM = (activeInput.startMonth - 1) + nextOffset
        val nextYear = activeInput.startYear + (totalM / 12)
        val nextMonth = ((totalM % 12) + 12) % 12 + 1
        val nextLabel = if (nextRow != null) "$effectiveEmiDay ${nextRow.monthLabel}" else "Fully Paid"

        val checklist = buildClosureChecklist(
            categoryId = entity.categoryId,
            categoryName = entity.categoryName,
            categoryChecklistItems = category?.checklistItems.orEmpty()
        )

        return TrackedLoanSummary(
            entity = entity,
            input = activeInput,
            result = result,
            paidMonthNumbers = paidSet,
            completedChecklist = checklistSet,
            nextDueMonthNumber = nextNum,
            nextDueDateLabel = nextLabel,
            nextDueYear = nextYear,
            nextDueMonth = nextMonth,
            emiDayOfMonth = effectiveEmiDay,
            autoDebitNach = entity.autoDebitNach,
            outstandingPrincipal = outstanding,
            totalPaidSoFar = LoanCalculationEngine.roundPaisa(totalPaidSoFar),
            interestSaved = interestSaved,
            monthsSaved = monthsSaved,
            progressFraction = progress,
            checklistItems = checklist
        )
    }

    fun computePortfolioMetrics(
        loans: List<TrackedLoanSummary>,
        monthlyIncome: Double
    ): PortfolioMetrics {
        val totalDebt = loans.sumOf { it.outstandingPrincipal }
        val activeLoans = loans.filter { it.outstandingPrincipal > 0.01 && it.nextDueMonthNumber != null }
        val activeEmi = activeLoans.sumOf { summary ->
            val nextRow = summary.result.schedule.firstOrNull { it.monthNumber == summary.nextDueMonthNumber }
            nextRow?.emiPaid ?: summary.result.monthlyEmi
        }
        val dti = if (monthlyIncome > 0.0) (activeEmi / monthlyIncome) * 100.0 else 0.0
        val totalInterestSaved = loans.sumOf { it.interestSaved }
        val totalMonthsSaved = loans.sumOf { it.monthsSaved }
        return PortfolioMetrics(
            totalOutstandingDebt = LoanCalculationEngine.roundPaisa(totalDebt),
            totalMonthlyEmiBurden = LoanCalculationEngine.roundPaisa(activeEmi),
            dtiPercent = LoanCalculationEngine.roundPaisa(dti),
            activeLoanCount = activeLoans.size,
            totalInterestSaved = LoanCalculationEngine.roundPaisa(totalInterestSaved),
            totalMonthsSaved = totalMonthsSaved
        )
    }

    fun selectNextEmiWidgetData(
        loans: List<TrackedLoanSummary>,
        currency: CurrencyCode
    ): NextEmiWidgetData {
        val activeLoans = loans.filter { it.nextDueMonthNumber != null && it.outstandingPrincipal > 0.01 }
        if (activeLoans.isEmpty()) {
            return NextEmiWidgetData(hasActiveLoan = false)
        }
        val soonest = activeLoans.minWith(
            compareBy<TrackedLoanSummary>(
                { it.nextDueYear },
                { it.nextDueMonth },
                { it.emiDayOfMonth }
            )
        )
        val nextRow = soonest.result.schedule.firstOrNull { it.monthNumber == soonest.nextDueMonthNumber }
        val emiAmount = nextRow?.emiPaid ?: soonest.result.monthlyEmi
        val totalMonthly = activeLoans.sumOf { s ->
            s.result.schedule.firstOrNull { it.monthNumber == s.nextDueMonthNumber }?.emiPaid ?: s.result.monthlyEmi
        }
        return NextEmiWidgetData(
            hasActiveLoan = true,
            loanId = soonest.entity.id,
            nickname = soonest.entity.nickname,
            lender = soonest.entity.lender,
            emiAmountFormatted = CurrencyFormatter.format(emiAmount, currency, false),
            dueDateLabel = soonest.nextDueDateLabel,
            emiDayOfMonth = soonest.emiDayOfMonth,
            autoDebitNach = soonest.autoDebitNach,
            activeLoanCount = activeLoans.size,
            totalMonthlyBurdenFormatted = CurrencyFormatter.format(totalMonthly, currency, false)
        )
    }
}
