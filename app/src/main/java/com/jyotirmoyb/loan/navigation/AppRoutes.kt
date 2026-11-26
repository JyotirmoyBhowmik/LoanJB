package com.jyotirmoyb.loan.navigation

sealed class AppRoute(val route: String) {
    // 5 Primary Bottom Navigation Tabs
    data object CalculatorHub : AppRoute("tab_calculator_hub")
    data object EmiCalculator : AppRoute("tab_emi_calculator")
    data object Articles : AppRoute("tab_articles")
    data object Market : AppRoute("tab_market")
    data object Profile : AppRoute("tab_profile")

    // Feature Destinations
    data object EmiResults : AppRoute("emi_results")
    data object LoanComparison : AppRoute("loan_comparison")
    data object LoanEligibility : AppRoute("loan_eligibility")
    data object BalanceTransfer : AppRoute("balance_transfer")
    data object LoanTaxBenefit : AppRoute("loan_tax_benefit")
    data object PartPaymentPlanner : AppRoute("part_payment_planner")
    data object BankStaffLoan : AppRoute("bank_staff_loan")
    data object SavedLoans : AppRoute("saved_loans")
    data object LoanGuide : AppRoute("loan_guide")
    data object DataAndRates : AppRoute("data_and_rates")
    data object Privacy : AppRoute("privacy_security")
    data object AboutLegal : AppRoute("about_legal_credits")

    // Bank Deposits Destinations
    data object FixedDeposit : AppRoute("fixed_deposit")
    data object FdInterestPayout : AppRoute("fd_interest_payout")
    data object RecurringDeposit : AppRoute("recurring_deposit")
    data object SmallSavings : AppRoute("small_savings")
    data object DepositComparison : AppRoute("deposit_comparison")

    // Mutual Fund Destinations
    data object MfSip : AppRoute("mf_sip")
    data object MfLumpsum : AppRoute("mf_lumpsum")
    data object MfSwp : AppRoute("mf_swp")
    data object MfGoal : AppRoute("mf_goal")
    data object MfXirr : AppRoute("mf_xirr")
    data object MfTaxGains : AppRoute("mf_tax_gains")
    data object MfLiveNav : AppRoute("mf_live_nav")

    // Taxation Destinations
    data object IncomeTax : AppRoute("tax_income_tax")
    data object HraCalculator : AppRoute("tax_hra")
    data object GstCalculator : AppRoute("tax_gst")
    data object AdvanceTaxTds : AppRoute("tax_advance_tds")

    // Retirement & Pension Destinations
    data object RetirementPlanner : AppRoute("ret_planner")
    data object NpsCalculator : AppRoute("ret_nps")
    data object EpfCalculator : AppRoute("ret_epf")
    data object ApyCalculator : AppRoute("ret_apy")
    data object PmsymCalculator : AppRoute("ret_pmsym")
    data object PmvvyCalculator : AppRoute("ret_pmvvy")

    // Wealth & Life Planners Destinations
    data object GoalPlanner : AppRoute("planner_goals")
    data object FinancialRules : AppRoute("planner_rules")
    data object WhereToInvest : AppRoute("planner_where_to_invest")

    data object ComingSoon : AppRoute("coming_soon/{moduleId}") {
        const val ARG_MODULE_ID = "moduleId"
        fun createRoute(moduleId: String): String = "coming_soon/$moduleId"
    }
}
