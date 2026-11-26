# LoanJB — Offline-First Indian Personal Finance, Loan & Tax Planner

**LoanJB** (`com.jyotirmoyb.loan`) is a production-grade, 100% offline-first Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room (v5)**, **DataStore**, **Moshi**, **WorkManager**, and **AndroidX Security Crypto**.

It provides deterministic `BigDecimal` financial calculations for Indian loans, bank & post-office deposits, mutual funds (with streaming AMFI NAV caching), income tax & GST, retirement schemes, and multi-goal planning—with **zero hardcoded rates or slabs** and **zero analytics or cloud user-data collection**.

---

## 1. System Architecture

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                         UI & Presentation Layer (Compose)                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────────────┐  │
│  │  LoanJBNavGraph  │  │ DesignSystem     │  │ Feature Screens & Previews  │  │
│  │  + Chrome/TopBar │  │ (Spacing, Cards, │  │ • features/emi              │  │
│  │  + BottomBar     │  │  Loading/Empty/  │  │ • features/deposits         │  │
│  │  + Drawer        │  │  Error, Banners) │  │ • features/mutualfund       │  │
│  └────────┬─────────┘  └────────┬─────────┘  │ • features/tax              │  │
│           │                     │            │ • features/retirement       │  │
│           ▼                     ▼            │ • features/planners         │  │
│  ┌────────────────────────────────────────┐  │ • features/market & articles│  │
│  │   Destination-Scoped ViewModels        │  │ • features/datarates & legal│  │
│  │   (StateFlow + UserProfile Defaults)   │  └─────────────────────────────┘  │
└──┴───────────────────┬────────────────────┴───────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                     Pure Kotlin Domain & Math Engines                         │
│  • MoneyMath (BigDecimal HALF_UP, exact paise residual absorption -> ₹0.00)   │
│  • LoanCalculationEngine + LoanSpecialtyCalculators + FixedRateLoanCalculator │
│  • LoanInsightsCalculator (Sensitivity, Flat vs Reducing, BT, Top-Up, Tax)    │
│  •LoanTrackerEngine (Prepayment / Floating Rate-Reset schedule recomputation) │
│  • DepositCalculationEngine + SmallSavingsEngine                              │
│  • MutualFundCalculationEngine + AmfiNavStreamParser + CapitalGainsTaxEngine  │
│  • IncomeTaxCalculationEngine + HraGstTdsEngine                               │
│  • RetirementEngines (Retirement, NPS, EPF/EPS, APY, PM-SYM, PMVVY)           │
│  • GoalPlannerEngine + FinancialRulesEngine + WhereToInvestEngine             │
│  • BackupCryptoEngine (AES-256-GCM + PBKDF2-HMAC-SHA256)                      │
└──────────────────────┬────────────────────────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                    4-Tier Config Resolver & Local Data Layer                  │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ ConfigDataRepository (4-Tier Priority Resolution + Provenance Badges)   │  │
│  │   1. USER_OVERRIDE  (DataStore per-key user edits)                      │  │
│  │   2. REMOTE_CACHED  (Verified remote JSON cached on disk)               │  │
│  │   3. BUNDLED_DEFAULT(app/src/main/assets/{rates,schemes,taxslabs}.json) │  │
│  │   4. FALLBACK       (Safe schema floor if asset missing)                │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│  ┌─────────────────────────┐  ┌────────────────────┐  ┌────────────────────┐  │
│  │ Room Database (v5)      │  │ Preferences &      │  │ Optional Anonymous │  │
│  │ • saved_calculations    │  │ Hardware Crypto    │  │ GET Workers        │  │
│  │ • comparison_scenarios  │  │ • UserPreferences  │  │ • AmfiNavSyncWorker│  │
│  │ • amfi_nav_schemes      │  │   (DataStore)      │  │   (Streaming)      │  │
│  │ • market_rate_history   │  │ • SecureKeyStore   │  │ • RemoteConfig     │  │
│  │ • article_bookmarks     │  │   (AES256-GCM)     │  │   Fetcher          │  │
│  │ (Schemas in app/schemas)│  └────────────────────┘  └────────────────────┘  │
│  └─────────────────────────┘                                                  │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. How to Add a New Calculator

LoanJB follows a strict **Data-Driven Engine + ViewModel + Compose Screen** pattern so new calculators automatically inherit currency formatting, user profile defaults, provenance badges, and disclaimers.

### Step 1: Add Configurable Rates/Rules to Assets (Never Hardcode)
Add any default interest rates, caps, or thresholds to the appropriate asset file in `app/src/main/assets/`:
- **`rates.json`**: Benchmark rates, bank FD/RD bands, loan default rates, or commodity reference rates.
- **`schemes.json`**: Government / Post Office / pension scheme rules, limits, and open/closed status.
- **`taxslabs.json`**: Income-tax slabs, deductions, rebates, surcharge, cess, or GST slabs.

### Step 2: Create the Pure Kotlin Domain Engine
1. Create a pure Kotlin calculation function/object using `MoneyMath` (`BigDecimal` with `RoundingMode.HALF_UP` at 2 decimal places for paise accuracy).
2. Read all rate defaults and caps from the resolved config model (`ResolvedConfigItem` / `SchemeDefinition` / `TaxFinancialYearConfig`) passed into the engine.
3. Add comprehensive unit tests in `app/src/test/java/com/jyotirmoyb/loan/` covering normal inputs, boundary values, zero/invalid inputs, and rounding invariants.

### Step 3: Create the ViewModel & Wire Profile Defaults
1. Create a `ViewModel` observing `ConfigDataRepository` and `UserPreferencesRepository.preferencesFlow`.
2. Pre-populate user defaults (such as monthly income, age, tax regime, risk profile, and currency) while allowing per-screen adjustments and one-tap `"Reset to default"`.

### Step 4: Build the Compose Screen & Register the Route
1. Build the screen in `app/src/main/java/com/jyotirmoyb/loan/features/<module>/` (keeping each file under ~500 lines) using components from `ui/components/` and `ui/designsystem/LoanJBDesignSystem.kt`:
   - `ValueSourceBadgesRow` to display the provenance badge (`Bundled default`, `Updated <date>`, or `User edited`) and `"As of"` date.
   - `ResetToDefaultAction` whenever a rate/parameter is overridden.
   - `DisclaimerBanner` / `FinancialDisclaimerBanner` for the mandatory `"Not a financial advisor"` notice.
   - Unique `Modifier.testTag("snake_case_tag")` on all interactive controls and `minimumInteractiveComponentSize()` (≥48dp touch targets).
2. Add the route to `navigation/AppRoutes.kt`, wire the composable in the corresponding `navigation/*NavRoutes.kt` file, and add a tile in `CalculatorHubScreen.kt` (or section hub).

---

## 3. How to Update Remote-Config Files (`rates.json`, `schemes.json`, `taxslabs.json`, `articles.json`)

All financial rates and tax laws are stored in versioned JSON files under `app/src/main/assets/` and mirrored by the remote config schema:

| File | Purpose | Key Metadata Fields |
| :--- | :--- | :--- |
| `app/src/main/assets/rates.json` | RBI Repo/SDF/MSF/CRR/SLR, loan benchmark defaults, bank FD/RD rates, Gold/Silver, USD-INR, Sensex/Nifty, 10Y G-Sec, inflation & planner defaults | `schemaVersion`, `version`, `asOf`, `lastVerified`, `source`, `items` |
| `app/src/main/assets/schemes.json` | PPF, SSY, SCSS, NSC, KVP, POMIS, MSSC, EPF/EPS, NPS, APY, PM-SYM, PMVVY rules & `isOpenToNewEntries` flags | `schemaVersion`, `version`, `asOf`, `lastVerified`, `source`, `schemes` |
| `app/src/main/assets/taxslabs.json` | Versioned per-FY New vs Old regime slabs, standard deduction, 87A rebate & marginal relief, surcharge, 4% cess, HRA, GST slabs, Capital Gains rules | `schemaVersion`, `version`, `defaultFy`, `asOf`, `lastVerified`, `financialYears` |
| `app/src/main/assets/articles.json` | Metadata catalog for the 32+ bundled offline Markdown guides (`assets/articles/*.md`) and remote article updates | `version`, `asOf`, `articles` |

### Updating a Rate or Rule
1. Open the target JSON file in `app/src/main/assets/`.
2. Increment the top-level `"version"` integer.
3. Update `"asOf"` and `"lastVerified"` (ISO `YYYY-MM-DD`).
4. For individual items in `rates.json`, update `"value"`, `"asOf"`, `"source"`, and optional `"min"` / `"max"` validation bounds.
5. For senior/government schemes in `schemes.json` that close or reopen to new enrollments, set `"isOpenToNewEntries": false` (or `true`) and update `"statusBannerText"`.

---

## 4. How to Verify and Publish New Rates

Before publishing an updated JSON bundle (either via an app update or your remote-config HTTPS endpoint):

1. **Validate Schema & Bounds via Unit Tests:**
   ```bash
   gradle :app:testDebugUnitTest --tests "com.jyotirmoyb.loan.data.ConfigResolverAndSchemaTest" --console=plain
   ```
   This test parses `rates.json`, `schemes.json`, and `taxslabs.json`, verifies that every required key exists, checks min/max bounds, and confirms that the 4-tier resolver (`USER_OVERRIDE` > `REMOTE_CACHED` > `BUNDLED_DEFAULT` > `FALLBACK`) resolves every item cleanly.
2. **Validate Tax Slab Boundaries & Rebate Edge Cases:**
   ```bash
   gradle :app:testDebugUnitTest --tests "com.jyotirmoyb.loan.features.tax.TaxCalculationEnginesTest" --console=plain
   ```
3. **In-App Inspection:**
   Open **Profile → Data & Rates** (or tap the Data & Rates icon in the top bar) to inspect every rate grouped by category (`Loans`, `Bank Deposits`, `Govt Schemes`, `Tax`, `Macro & Gold`), verify its `"As of"` date and source chip, and test `"Check for updated rates"`.

---

## 5. How to Build, Test, and Release

### Prerequisites
- **JDK 17**
- **Android SDK 36** (`minSdk = 26`, `targetSdk = 36`)

### Run Full Unit, Robolectric UI, Room Migration & Performance Benchmark Suite
```bash
gradle :app:testDebugUnitTest --console=plain
```

### Generate JaCoCo Code Coverage Report
```bash
gradle :app:jacocoTestReport --console=plain
```
Reports are generated at:
- HTML: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- XML: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

### Verify R8 Minification & ProGuard Rules
```bash
gradle :app:minifyReleaseWithR8 --console=plain
```

### Build Signed Release AAB / APK
Version numbers are managed in a single place in `gradle/libs.versions.toml` (`appVersionCode` and `appVersionName`). Supply your signing credentials via environment variables (without modifying `signingConfigs` in `app/build.gradle.kts`):

```bash
export KEYSTORE_PATH="/secure/path/to/upload-keystore.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

gradle :app:bundleRelease --console=plain
```

See **[`RELEASE.md`](RELEASE.md)** for the complete pre-release verification checklist and **[`PLAY_DATA_SAFETY.md`](PLAY_DATA_SAFETY.md)** for the Google Play Data Safety documentation.
