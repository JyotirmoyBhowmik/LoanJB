# Changelog

## [2.1.0] — Build Hardening, Keystore AES-GCM Storage & Modularization

### Added
- **Gradle Wrapper (Gradle 9.3.1)**: Added `gradlew`, `gradlew.bat`, and `gradle/wrapper/` pinned to Gradle 9.3.1 for AGP 9.1.1 compatibility.
- **Static Analysis (`ktlint`)**: Added `org.jlleitschuh.gradle.ktlint` with `.editorconfig` and a baseline (`app/config/ktlint/baseline.xml`) so CI passes on existing code and fails on new lint violations.
- **Android Keystore + AES-256-GCM Encrypted Preferences (`KeystoreEncryptedPrefs`)**: Replaced active `EncryptedSharedPreferences` usage in `AppLockManager` and `SecureApiKeyStore` with a Keystore-backed AES-256-GCM wrapper with AAD key-binding, automatic one-time migration from legacy `EncryptedSharedPreferences` files, immediate legacy file deletion, and updated backup exclusion rules (`backup_rules.xml`, `data_extraction_rules.xml`).
- **Security & Migration Unit Tests**: Added `KeystoreEncryptedPrefsAndMigrationTest` covering AES-GCM tamper/AAD checks, PIN setup/verification/clearing, API key save/load/mask/clear, and one-time legacy migration idempotency.

### Changed
- **Unified CI Workflow**: Merged `.github/workflows/android-build.yml` and `.github/workflows/android-ci.yml` into a single `android-ci.yml` workflow using `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 17), `gradle/actions/setup-gradle@v4`, and `./gradlew` for `ktlintCheck`, `testDebugUnitTest`, `jacocoTestReport`, `lintDebug`, `assembleDebug`, and `minifyReleaseWithR8` / `bundleRelease` with a throwaway CI key; kept `validate-config.yml`.
- **Dependencies**: Updated Compose BOM (`2026.06.01`) and AndroidX libraries (`core-ktx` 1.18.0, `lifecycle` 2.10.0, `activity-compose` 1.13.0, `navigation-compose` 2.9.8, `room` 2.8.5, `work` 2.12.0, `datastore` 1.3.0, `appcompat` 1.8.0, `profileinstaller` 1.4.1, `security-crypto` 1.1.0 stable for one-time migration) to latest stable versions compatible with `compileSdk 36`; updated `LoanTypeDropdown` to `ExposedDropdownMenuAnchorType`.
- **Narrowed R8 Rules (`app/proguard-rules.pro`)**: Replaced broad `data.**` and `domain.**` wildcard keep rules with targeted rules for Moshi `@JsonClass` models/adapters, Room entities/DAOs, WorkManager workers, BroadcastReceivers, and AppWidgetProvider.
- **File Modularization (≤600 lines)**: Split 7 large files without changing behavior:
  - `LoanCalculationEngine.kt` → extracted `LoanScheduleSimulator.kt`
  - `RetirementEngines.kt` → extracted `EpfAndPensionEngines.kt`
  - `LoanComparisonScreen.kt` → extracted `LoanComparisonCharts.kt`
  - `DataRatesScreen.kt` → extracted `DataRatesComponents.kt`
  - `FixedDepositScreen.kt` → extracted `DepositSharedComponents.kt`
  - `ExploreScreen.kt` → extracted `ExploreDetailComponents.kt`
  - `IncomeTaxScreen.kt` → extracted `IncomeTaxComponents.kt`
- **Version Bump**: Bumped `appVersionCode` to `2` and `appVersionName` to `2.1.0` in `gradle/libs.versions.toml`.

### Removed
- Removed unused `google-services` and `secrets` Gradle plugins, commented-out Firebase/Camera/Coil dependencies, `googleServices.missing.passthrough`, redundant `android-build.yml` workflow, and the placeholder static-analysis file-existence step in CI.

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
