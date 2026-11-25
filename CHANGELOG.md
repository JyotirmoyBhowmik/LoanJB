# Changelog

## [2.0.0] — MoneyWink-Style Indian Financial Hub Redesign

### Kept Untouched (Core Engine, Persistence & Utilities)
- `engine/LoanCalculationEngine.kt`, `engine/LoanModels.kt`, `engine/CatalogModels.kt`, `engine/CurrencyFormatter.kt` — preserved with all original amortization, floating-rate sensitivity, flat-vs-reducing EAR, offset, moratorium, tranche, flexi, bill discounting, margin call, and prepayment logic intact.
- `data/LoanEntities.kt`, `data/LoanDao.kt`, `data/LoanDatabase.kt` — Room database schema preserved with zero migration disruption.
- `util/ScheduleExporter.kt`, `util/EmiReminderReceiver.kt`, `util/EmiReminderScheduler.kt` — PDF, CSV, JSON backup export and AlarmManager reminders preserved.
- `app/src/main/assets/catalog.json` and `ui/screens/CatalogEditorScreen.kt` — editable catalog preserved.
- `ui/screens/ExploreScreen.kt`, `ui/screens/MyLoansScreen.kt`, `ui/screens/CompareScreen.kt`, `ui/screens/SettingsScreen.kt`, `ui/components/Charts.kt` — preserved and relocated cleanly under the new Hub and Profile navigation.

### Added
- **5-Tab Navigation Compose Architecture** (`navigation/AppRoutes.kt`, `navigation/LoanJBNavGraph.kt`): `Calculator | EMI | Articles | Market | Profile` with per-tab back stack preservation and adaptive `NavigationRail` for tablets/foldables.
- **Extensible Calculator Registry** (`features/CalculatorRegistry.kt`): 6 Indian personal finance sections (`WEALTH & LIFE PLANNERS`, `LOANS & EMI`, `BANK DEPOSITS`, `MUTUAL FUND`, `TAXATION`, `RETIREMENT & PENSION`) and `CalculatorEngineContract<I, O>`.
- **Home Hub** (`features/hub/HubScreen.kt`, `HubViewModel.kt`): MoneyWink-style section cards with sage-green accent bars, tag chips, 2-column module tiles, live search filter, and offline Workspace shortcuts (`Saved Loans`, `Loan Types Guide`).
- **4-Screen Swipeable First-Launch Onboarding** (`features/onboarding/OnboardingScreen.kt`): Warm beige & sage-green walkthrough persisted via DataStore (`onboardingCompleted`), replayable from Profile.
- **Modular EMI Calculator** (`features/emi/`): Split into `LoanTypeDropdown`, `AmountField` (with `"28.0 Lakh"` helper badge), `RateField`, `TenureField` (`Year | Month` toggle), `StartDateRow` (`Today` / `Change` picker), `AdvanceSettingsCard` (`"N Active"` badge + checkbox/info/expand rows), `VariantAdvanceRows`, and `EmiInputScreen`.
- **Modular EMI Results** (`features/emi/results/`): `EmiResultsScreen` with 3-column Summary Strip and separate `YearlyScheduleTab`, virtualized `MonthlyScheduleTab` (smooth 360-row `LazyColumn` with `"YEAR 1 · Oct – Sep"` navy banners, bold Total rows, and highlighted prepayment rows), and `GraphTab`.
- **Bank Staff Loan Calculator** (`features/bankstaff/`): Editable Indian bank staff loan presets (Housing, Car, Clean OD, Child Education) with side-by-side staff-vs-market interest savings.
- **Per-Feature ViewModels** (`HubViewModel`, `EmiViewModel`, `BankStaffViewModel`, `MyLoansViewModel`, `CompareViewModel`, `ProfileViewModel`) backed by shared `AppContainer`.
- **R8 Release Hardening**: Enabled `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts` with Room/Moshi/Coroutines ProGuard rules in `app/proguard-rules.pro`.
- **Tests**: Added known-answer 28L @ 10.5% 6-year prepayment tests (`REDUCE_TENURE` vs `REDUCE_EMI`) in `LoanCalculationEngineTest.kt` and Robolectric Compose UI tests in `LoanJBUiTest.kt`.

### Replaced / Removed
- Replaced monolithic `ui/screens/CalculatorScreen.kt` (1,160 lines) and `ui/screens/CalculatorResultSection.kt` (722 lines) with modular composables under `features/emi/` and `features/emi/results/`.
- Replaced monolithic `viewmodel/LoanJBViewModel.kt` (637 lines) with per-feature ViewModels and `viewmodel/UiModels.kt`.
- Updated Material 3 color palette (`ui/theme/Color.kt`, `Theme.kt`) to MoneyWink sage green (`#5B8A5A`), teal (`#1FB5A0`), slate navy (`#1E2A4A`), amber interest (`#E67E22`), and 16dp cards across Light & Dark modes.
