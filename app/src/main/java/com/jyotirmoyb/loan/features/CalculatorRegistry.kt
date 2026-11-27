package com.jyotirmoyb.loan.features

import androidx.compose.ui.graphics.Color
import com.jyotirmoyb.loan.navigation.AppRoute

/**
 * Common contract implemented by every calculator engine module so future calculators
 * (SIP, FD, Tax, etc.) plug in cleanly without changing the Hub or Navigation shell.
 */
interface CalculatorEngineContract<I, O> {
    val id: String
    fun compute(input: I): O
}

enum class CalculatorSection(val title: String, val tag: String, val tagBgColor: Color, val tagTextColor: Color, val iconKey: String) {
    WEALTH_PLANNERS(
        title = "WEALTH & LIFE PLANNERS",
        tag = "FEATURED",
        tagBgColor = Color(0xFFE3EFE2),
        tagTextColor = Color(0xFF3E6A3E),
        iconKey = "featured"
    ),
    LOANS_EMI(
        title = "LOANS & EMI",
        tag = "LOANS",
        tagBgColor = Color(0xFFDDF6F2),
        tagTextColor = Color(0xFF138D7B),
        iconKey = "loans"
    ),
    BANK_DEPOSITS(
        title = "BANK DEPOSITS",
        tag = "DEPOSITS",
        tagBgColor = Color(0xFFE3EFE2),
        tagTextColor = Color(0xFF3E6A3E),
        iconKey = "deposits"
    ),
    MUTUAL_FUND(
        title = "MUTUAL FUND",
        tag = "WEALTH",
        tagBgColor = Color(0xFFDDF6F2),
        tagTextColor = Color(0xFF138D7B),
        iconKey = "mutual"
    ),
    TAXATION(
        title = "TAXATION",
        tag = "TAX",
        tagBgColor = Color(0xFFFFF3E0),
        tagTextColor = Color(0xFFB45309),
        iconKey = "tax"
    ),
    RETIREMENT_PENSION(
        title = "RETIREMENT & PENSION",
        tag = "PENSION",
        tagBgColor = Color(0xFFE8ECF5),
        tagTextColor = Color(0xFF1E2A4A),
        iconKey = "pension"
    )
}

data class CalculatorModule(
    val id: String,
    val section: CalculatorSection,
    val title: String,
    val subtitle: String = "",
    val badgeText: String? = null,
    val icon: String,
    val route: String,
    val enabled: Boolean
)

/**
 * Single registry driving the Home Hub.
 * To add a future calculator:
 *   1) Create features/<name>/
 *   2) Register one CalculatorModule entry here with enabled = true
 *   3) Wire one composable route in LoanJBNavGraph.kt
 */
object CalculatorRegistry {

    val modules: List<CalculatorModule> = listOf(
        // 1. WEALTH & LIFE PLANNERS (FEATURED) — Fully Active!
        CalculatorModule(
            id = "goal_planner",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Goal Planner",
            subtitle = "Multi-goal SIP & inflation",
            icon = "target",
            route = AppRoute.GoalPlanner.route,
            enabled = true
        ),
        CalculatorModule(
            id = "retirement_plan",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Retirement Plan",
            subtitle = "Corpus, SIP & shortfall",
            icon = "umbrella",
            route = AppRoute.RetirementPlanner.route,
            enabled = true
        ),
        CalculatorModule(
            id = "financial_rules",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Financial Rules",
            subtitle = "50/30/20, emergency & cover",
            icon = "sparkles",
            route = AppRoute.FinancialRules.route,
            enabled = true
        ),
        CalculatorModule(
            id = "where_to_invest",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Where to Invest",
            subtitle = "Educational asset mix",
            icon = "lightbulb",
            route = AppRoute.WhereToInvest.route,
            enabled = true
        ),

        // 2. LOANS & EMI (LOANS) — Fully Active!
        CalculatorModule(
            id = "loan_emi",
            section = CalculatorSection.LOANS_EMI,
            title = "Loan (EMI)",
            subtitle = "Home, Car, Personal & more",
            badgeText = "EMI",
            icon = "emi",
            route = AppRoute.EmiCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "loan_comparison",
            section = CalculatorSection.LOANS_EMI,
            title = "Loan Comparison",
            subtitle = "Compare up to 3 offers",
            icon = "compare",
            route = AppRoute.LoanComparison.route,
            enabled = true
        ),
        CalculatorModule(
            id = "loan_eligibility",
            section = CalculatorSection.LOANS_EMI,
            title = "Eligibility & FOIR",
            subtitle = "Max loan & reverse EMI",
            icon = "eligibility",
            route = AppRoute.LoanEligibility.route,
            enabled = true
        ),
        CalculatorModule(
            id = "balance_transfer",
            section = CalculatorSection.LOANS_EMI,
            title = "Balance Transfer",
            subtitle = "Break-even & Top-Up",
            icon = "balance_transfer",
            route = AppRoute.BalanceTransfer.route,
            enabled = true
        ),
        CalculatorModule(
            id = "loan_tax_benefit",
            section = CalculatorSection.LOANS_EMI,
            title = "Loan Tax Benefit",
            subtitle = "Old vs New Regime 24b/80E",
            icon = "tax_benefit",
            route = AppRoute.LoanTaxBenefit.route,
            enabled = true
        ),
        CalculatorModule(
            id = "part_payment_planner",
            section = CalculatorSection.LOANS_EMI,
            title = "Part-Payment Plan",
            subtitle = "Target loan-free date",
            icon = "part_payment",
            route = AppRoute.PartPaymentPlanner.route,
            enabled = true
        ),
        CalculatorModule(
            id = "bank_staff_loan",
            section = CalculatorSection.LOANS_EMI,
            title = "Bank Staff Loan",
            subtitle = "Editable staff presets",
            badgeText = "EMI",
            icon = "staff",
            route = AppRoute.BankStaffLoan.route,
            enabled = true
        ),

        // 3. BANK DEPOSITS (DEPOSITS) — Fully Active!
        CalculatorModule(
            id = "fixed_deposit",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "Fixed Deposit",
            subtitle = "Cumulative, senior & TDS",
            badgeText = "FD",
            icon = "fd",
            route = AppRoute.FixedDeposit.route,
            enabled = true
        ),
        CalculatorModule(
            id = "fd_interest_payout",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "FD Interest Payout",
            subtitle = "Monthly & quarterly income",
            badgeText = "FD",
            icon = "fd",
            route = AppRoute.FdInterestPayout.route,
            enabled = true
        ),
        CalculatorModule(
            id = "recurring_deposit",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "Recurring Deposit",
            subtitle = "Monthly savings & TDS",
            badgeText = "RD",
            icon = "rd",
            route = AppRoute.RecurringDeposit.route,
            enabled = true
        ),
        CalculatorModule(
            id = "small_savings",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "Small Savings",
            subtitle = "PPF, SSY, NSC, SCSS, PO",
            icon = "small_savings",
            route = AppRoute.SmallSavings.route,
            enabled = true
        ),
        CalculatorModule(
            id = "deposit_comparison",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "FD vs RD vs PPF",
            subtitle = "Post-tax vs Debt Fund",
            icon = "deposit_compare",
            route = AppRoute.DepositComparison.route,
            enabled = true
        ),

        // 4. MUTUAL FUND (WEALTH) — Fully Active!
        CalculatorModule(
            id = "sip",
            section = CalculatorSection.MUTUAL_FUND,
            title = "SIP Calculator",
            subtitle = "Step-up & inflation-adj.",
            badgeText = "SIP",
            icon = "sip",
            route = AppRoute.MfSip.route,
            enabled = true
        ),
        CalculatorModule(
            id = "lumpsum",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Lumpsum Calculator",
            subtitle = "One-time compounding",
            icon = "lumpsum",
            route = AppRoute.MfLumpsum.route,
            enabled = true
        ),
        CalculatorModule(
            id = "swp",
            section = CalculatorSection.MUTUAL_FUND,
            title = "SWP Calculator",
            subtitle = "Longevity & safe payout",
            badgeText = "SWP",
            icon = "swp",
            route = AppRoute.MfSwp.route,
            enabled = true
        ),
        CalculatorModule(
            id = "mf_goal",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Goal Calculator",
            subtitle = "Monthly SIP for target",
            icon = "target",
            route = AppRoute.MfGoal.route,
            enabled = true
        ),
        CalculatorModule(
            id = "mf_xirr",
            section = CalculatorSection.MUTUAL_FUND,
            title = "XIRR Calculator",
            subtitle = "Irregular dated flows",
            badgeText = "XIRR",
            icon = "xirr",
            route = AppRoute.MfXirr.route,
            enabled = true
        ),
        CalculatorModule(
            id = "mf_tax_gains",
            section = CalculatorSection.MUTUAL_FUND,
            title = "MF Tax on Gains",
            subtitle = "Equity vs Debt LTCG/STCG",
            icon = "tax_benefit",
            route = AppRoute.MfTaxGains.route,
            enabled = true
        ),
        CalculatorModule(
            id = "mf_live_nav",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Live NAV & Funds",
            subtitle = "AMFI search & favourites",
            badgeText = "NAV",
            icon = "live_nav",
            route = AppRoute.MfLiveNav.route,
            enabled = true
        ),

        // 5. TAXATION (TAX) — Fully Active!
        CalculatorModule(
            id = "income_tax",
            section = CalculatorSection.TAXATION,
            title = "Income Tax",
            subtitle = "New vs Old regime slabs",
            badgeText = "IT",
            icon = "tax",
            route = AppRoute.IncomeTax.route,
            enabled = true
        ),
        CalculatorModule(
            id = "hra",
            section = CalculatorSection.TAXATION,
            title = "House Rent (HRA)",
            subtitle = "Section 10(13A) exemption",
            badgeText = "HRA",
            icon = "hra",
            route = AppRoute.HraCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "gst",
            section = CalculatorSection.TAXATION,
            title = "GST Calculator",
            subtitle = "CGST, SGST, IGST & reverse",
            badgeText = "GST",
            icon = "gst",
            route = AppRoute.GstCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "advance_tax_tds",
            section = CalculatorSection.TAXATION,
            title = "Advance Tax & TDS",
            subtitle = "Due dates, FD/Salary TDS & CG",
            badgeText = "TDS",
            icon = "advance_tax",
            route = AppRoute.AdvanceTaxTds.route,
            enabled = true
        ),

        // 6. RETIREMENT & PENSION (PENSION) — Fully Active!
        CalculatorModule(
            id = "retirement_planner",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Retirement Planner",
            subtitle = "Inflation-adjusted corpus",
            badgeText = "RP",
            icon = "rp",
            route = AppRoute.RetirementPlanner.route,
            enabled = true
        ),
        CalculatorModule(
            id = "nps",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "National Pension Scheme",
            subtitle = "Annuity split & pension",
            badgeText = "NPS",
            icon = "nps",
            route = AppRoute.NpsCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "epf",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Employees Provident Fund",
            subtitle = "PF corpus, ceiling & EPS",
            badgeText = "EPF",
            icon = "epf",
            route = AppRoute.EpfCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "apy",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Atal Pension Yojana",
            subtitle = "Age & pension slab chart",
            icon = "pension",
            route = AppRoute.ApyCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "pmsym",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "PM Shram Yogi Maan-Dhan",
            subtitle = "1:1 Govt match & ₹3k pension",
            icon = "pension",
            route = AppRoute.PmsymCalculator.route,
            enabled = true
        ),
        CalculatorModule(
            id = "pmvvy",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "PMVVY & Senior Schemes",
            subtitle = "Open/closed status & payout",
            icon = "pension",
            route = AppRoute.PmvvyCalculator.route,
            enabled = true
        )
    )

    fun findById(id: String): CalculatorModule? = modules.firstOrNull { it.id == id }
}
