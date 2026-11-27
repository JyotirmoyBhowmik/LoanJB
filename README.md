# LoanJB — Offline-First Indian Personal Finance, Loan & Tax Planner

**LoanJB** (`com.jyotirmoyb.loan`) is a production-grade, 100% offline-first Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room (v5)**, **DataStore**, **Moshi**, **WorkManager**, and **Android Keystore AES-256-GCM**.

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

## 4. Updating Data with an AI Tool

You can keep LoanJB's RBI policy rates, bank FD bands, small-savings schemes, and income-tax slabs up to date without waiting for an app release by importing a validated JSON update bundle on-device.

### Step-by-Step Workflow
1. **Upload the brief to your AI tool:** Open your preferred AI assistant with web browsing enabled and upload **`LoanJB_Data_Update_Brief.md`** (which lists every tracked config key, expected unit, and official RBI / CBDT / India Post / DEA / PFRDA sources). Ask it to verify current official values and output a JSON file matching the LoanJB update-bundle format below.
2. **Import the JSON file on your phone:** Save the generated `.json` file (max **1 MB**) to your device, open LoanJB, and navigate to **Profile → Data & Rates → Import update file**.
3. **Preview every change before saving:** LoanJB validates every key, numeric type, unit, HTTPS `sourceUrl`, `effectiveFrom`, `lastVerified` (`YYYY-MM-DD`, rejecting `"UNVERIFIED"`), and tax-slab ordering before showing the **Preview Data Update** dialog:
   - Each rate change is shown as **`Label: old → new`**, along with its effective date and source name.
   - Untick any individual change or table you do not want to apply.
   - Inspect **Unresolved keys**, **Suggested new keys** (review-only), and **Update notes**.
4. **Apply (or Undo):** Tap **Apply all** (or **Apply selected**) to merge the updates into the local cache with source **`Imported`** (precedence: `Manual override` > `Imported / Online with newest dataVersion` > `Built-in`).
   - **Multi-part updates:** Large updates split into files named `PART 1/2`, `PART 2/2`, etc. (or marked in `notes` / `dataVersion`) can be imported one after another; each part merges cleanly on top of the previous part.
   - **Undo:** LoanJB retains the last **5** imports so you can revert with **Undo last import** at any time.

### Update-Bundle JSON Format
All top-level sections (`rates`, `taxslabs`, `schemes`, `confirmedUnchanged`, `unresolved`, `suggestedNewKeys`, `notes`) are optional; include only what changed or was verified:

```json
{
  "bundleVersion": 1,
  "generatedOn": "2026-11-27",
  "generatedBy": "Verified against RBI / DEA / CBDT",
  "dataVersion": "2026.11.27",
  "rates": {
    "items": [
      {
        "key": "rbi.repo_rate",
        "value": 5.5,
        "previousValue": 6.5,
        "unit": "% p.a.",
        "effectiveFrom": "2026-06-06",
        "sourceName": "RBI Monetary Policy Statement",
        "sourceUrl": "https://www.rbi.org.in/",
        "lastVerified": "2026-11-27"
      }
    ],
    "bankFdRates": [
      {
        "bankId": "sbi",
        "bankName": "State Bank of India (SBI)",
        "generalRate": 6.8,
        "seniorRate": 7.3,
        "tenureLabel": "2–3 yrs"
      }
    ]
  },
  "taxslabs": {
    "items": [],
    "taxYears": [],
    "gstSlabs": [0, 5, 12, 18, 28],
    "advanceTaxThreshold": 10000
  },
  "schemes": {
    "items": [],
    "seniorPensionSchemes": [],
    "apyTable": [],
    "pmsymTable": []
  },
  "confirmedUnchanged": ["scheme.ppf.rate", "tax.new.rebate_limit"],
  "unresolved": [
    { "key": "market.gold.24k_10g", "reason": "Intraday bullion quote varies by city" }
  ],
  "suggestedNewKeys": ["tax.fy2026_27.example_key"],
  "notes": ["PART 1/1: Verified RBI repo, SDF, MSF, CRR and SBI FD card rates."]
}
```

---

## 5. How to Verify and Publish New Rates

Before publishing an updated JSON bundle (either via an app update or your remote-config HTTPS endpoint):

1. **Validate Schema, Bounds & Update-Bundle Rules via Unit Tests:**
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "com.jyotirmoyb.loan.data.remote.*" --console=plain
   ```
2. **Validate Tax Slab Boundaries & Rebate Edge Cases:**
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "com.jyotirmoyb.loan.domain.TaxSuiteEnginesTest" --console=plain
   ```
3. **In-App Inspection:**
   Open **Profile → Data & Rates** (or tap the Data & Rates icon in the top bar) to inspect every rate grouped by category, verify its compact source & verification line (tap any badge to view the official link and legend), and test **Import update file** or **Check for updated rates**.

---

## 6. How to Build, Test, and Release

### Prerequisites
- **JDK 17**
- **Android SDK 36** (`minSdk = 26`, `targetSdk = 36`)

### Run Static Analysis, Unit/UI Tests, Lint & Builds
```bash
./gradlew :app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

### Generate JaCoCo Code Coverage Report
```bash
./gradlew :app:jacocoTestReport --console=plain
```
Reports are generated at:
- HTML: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- XML: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

### Verify R8 Minification & ProGuard Rules
```bash
./gradlew :app:minifyReleaseWithR8 --console=plain
```

### Build Signed Release AAB / APK
Version numbers are managed in a single place in `gradle/libs.versions.toml` (`appVersionCode` and `appVersionName`). Supply your signing credentials via environment variables (without modifying `signingConfigs` in `app/build.gradle.kts`):

```bash
export KEYSTORE_PATH="/secure/path/to/upload-keystore.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

./gradlew :app:bundleRelease --console=plain
```

See **[`RELEASE.md`](RELEASE.md)** for the complete pre-release verification & GitHub Pages checklist, **[`docs/privacy-policy.md`](docs/privacy-policy.md)**, **[`docs/terms.md`](docs/terms.md)**, and **[`PLAY_DATA_SAFETY.md`](PLAY_DATA_SAFETY.md)**.
