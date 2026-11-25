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

enum class CalculatorSection(
    val title: String,
    val tag: String,
    val tagBgColor: Color,
    val tagTextColor: Color,
    val iconKey: String
) {
    WEALTH_PLANNERS(
        title = "WEALTH & LIFE PLANNERS",
        tag = "FEATURED",
        tagBgColor = Color(0xFFDDF6F2),
        tagTextColor = Color(0xFF138D7B),
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
        tagBgColor = Color(0xFFE0F2FE),
        tagTextColor = Color(0xFF0369A1),
        iconKey = "deposits"
    ),
    MUTUAL_FUND(
        title = "MUTUAL FUND",
        tag = "WEALTH",
        tagBgColor = Color(0xFFE0F7F4),
        tagTextColor = Color(0xFF0F766E),
        iconKey = "mutual"
    ),
    TAXATION(
        title = "TAXATION",
        tag = "TAX",
        tagBgColor = Color(0xFFFEF3C7),
        tagTextColor = Color(0xFFB45309),
        iconKey = "tax"
    ),
    RETIREMENT_PENSION(
        title = "RETIREMENT & PENSION",
        tag = "PENSION",
        tagBgColor = Color(0xFFEDE9FE),
        tagTextColor = Color(0xFF6D28D9),
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
        // 1. WEALTH & LIFE PLANNERS (FEATURED)
        CalculatorModule(
            id = "goal_planner",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Goal Planner",
            subtitle = "Target savings for dreams",
            icon = "target",
            route = AppRoute.ComingSoon.createRoute("goal_planner"),
            enabled = false
        ),
        CalculatorModule(
            id = "retirement_plan",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Retirement Plan",
            subtitle = "Build pension & corpus",
            icon = "umbrella",
            route = AppRoute.ComingSoon.createRoute("retirement_plan"),
            enabled = false
        ),
        CalculatorModule(
            id = "financial_rules",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Financial Rules",
            subtitle = "50/30/20 & budget limits",
            icon = "sparkles",
            route = AppRoute.ComingSoon.createRoute("financial_rules"),
            enabled = false
        ),
        CalculatorModule(
            id = "where_to_invest",
            section = CalculatorSection.WEALTH_PLANNERS,
            title = "Where to Invest",
            subtitle = "Asset allocation guide",
            icon = "lightbulb",
            route = AppRoute.ComingSoon.createRoute("where_to_invest"),
            enabled = false
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
            subtitle = "Compare 2-3 bank offers",
            icon = "compare",
            route = AppRoute.LoanComparison.route,
            enabled = true
        ),
        CalculatorModule(
            id = "bank_staff_loan",
            section = CalculatorSection.LOANS_EMI,
            title = "Bank Staff Loan",
            subtitle = "Subsidized staff presets",
            badgeText = "EMI",
            icon = "staff",
            route = AppRoute.BankStaffLoan.route,
            enabled = true
        ),

        // 3. BANK DEPOSITS (DEPOSITS)
        CalculatorModule(
            id = "fixed_deposit",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "Fixed Deposit",
            subtitle = "Cumulative maturity value",
            badgeText = "FD",
            icon = "fd",
            route = AppRoute.ComingSoon.createRoute("fixed_deposit"),
            enabled = false
        ),
        CalculatorModule(
            id = "fd_interest_payout",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "FD Interest Payout",
            subtitle = "Monthly & quarterly income",
            badgeText = "FD",
            icon = "fd",
            route = AppRoute.ComingSoon.createRoute("fd_interest_payout"),
            enabled = false
        ),
        CalculatorModule(
            id = "recurring_deposit",
            section = CalculatorSection.BANK_DEPOSITS,
            title = "Recurring Deposit",
            subtitle = "Monthly savings maturity",
            badgeText = "RD",
            icon = "rd",
            route = AppRoute.ComingSoon.createRoute("recurring_deposit"),
            enabled = false
        ),

        // 4. MUTUAL FUND (WEALTH)
        CalculatorModule(
            id = "sip",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Systematic Investment Plan",
            subtitle = "Monthly mutual fund SIP",
            badgeText = "SIP",
            icon = "sip",
            route = AppRoute.ComingSoon.createRoute("sip"),
            enabled = false
        ),
        CalculatorModule(
            id = "swp",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Systematic Withdrawal Plan",
            subtitle = "Regular corpus payout",
            badgeText = "SWP",
            icon = "swp",
            route = AppRoute.ComingSoon.createRoute("swp"),
            enabled = false
        ),
        CalculatorModule(
            id = "lumpsum",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Lumpsum Calculator",
            subtitle = "One-time compounding",
            icon = "lumpsum",
            route = AppRoute.ComingSoon.createRoute("lumpsum"),
            enabled = false
        ),
        CalculatorModule(
            id = "mf_goal",
            section = CalculatorSection.MUTUAL_FUND,
            title = "Goal Calculator",
            subtitle = "Reverse SIP for target",
            icon = "target",
            route = AppRoute.ComingSoon.createRoute("mf_goal"),
            enabled = false
        ),

        // 5. TAXATION (TAX)
        CalculatorModule(
            id = "income_tax",
            section = CalculatorSection.TAXATION,
            title = "Income Tax",
            subtitle = "New vs Old regime slabs",
            badgeText = "IT",
            icon = "tax",
            route = AppRoute.ComingSoon.createRoute("income_tax"),
            enabled = false
        ),
        CalculatorModule(
            id = "hra",
            section = CalculatorSection.TAXATION,
            title = "House Rent (HRA)",
            subtitle = "Section 10(13A) exemption",
            badgeText = "HRA",
            icon = "hra",
            route = AppRoute.ComingSoon.createRoute("hra"),
            enabled = false
        ),
        CalculatorModule(
            id = "gst",
            section = CalculatorSection.TAXATION,
            title = "GST Calculator",
            subtitle = "Inclusive & exclusive GST",
            badgeText = "GST",
            icon = "gst",
            route = AppRoute.ComingSoon.createRoute("gst"),
            enabled = false
        ),

        // 6. RETIREMENT & PENSION (PENSION)
        CalculatorModule(
            id = "retirement_planner",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Retirement Planner",
            subtitle = "Inflation-adjusted corpus",
            badgeText = "RP",
            icon = "rp",
            route = AppRoute.ComingSoon.createRoute("retirement_planner"),
            enabled = false
        ),
        CalculatorModule(
            id = "nps",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "National Pension Scheme",
            subtitle = "80CCD(1B) & annuity split",
            badgeText = "NPS",
            icon = "nps",
            route = AppRoute.ComingSoon.createRoute("nps"),
            enabled = false
        ),
        CalculatorModule(
            id = "epf",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Employees Provident Fund",
            subtitle = "PF corpus at retirement",
            badgeText = "EPF",
            icon = "epf",
            route = AppRoute.ComingSoon.createRoute("epf"),
            enabled = false
        ),
        CalculatorModule(
            id = "apy",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "Atal Pension Yojana",
            subtitle = "Guaranteed monthly pension",
            icon = "pension",
            route = AppRoute.ComingSoon.createRoute("apy"),
            enabled = false
        ),
        CalculatorModule(
            id = "pmsym",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "PM Shram Yogi Maan-Dhan",
            subtitle = "Unorganized worker pension",
            icon = "pension",
            route = AppRoute.ComingSoon.createRoute("pmsym"),
            enabled = false
        ),
        CalculatorModule(
            id = "pmvvy",
            section = CalculatorSection.RETIREMENT_PENSION,
            title = "PM Vaya Vandhana",
            subtitle = "Senior citizen pension plan",
            icon = "pension",
            route = AppRoute.ComingSoon.createRoute("pmvvy"),
            enabled = false
        )
    )

    fun findById(id: String): CalculatorModule? = modules.firstOrNull { it.id == id }
}
