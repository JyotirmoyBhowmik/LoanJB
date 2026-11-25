# LoanJB — Offline Indian EMI Calculator & Loan Planner

**Package:** `com.jyotirmoyb.loan`  
**Stack:** Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · DataStore · Moshi  
**Privacy:** 100% offline. No login, no network calls, no trackers.

---

## Architecture Overview

```text
app/src/main/java/com/jyotirmoyb/loan/
├── MainActivity.kt                     # Onboarding gate + LoanJBTheme
├── navigation/
│   ├── AppRoutes.kt                    # Sealed route definitions (5 tabs + feature routes)
│   └── LoanJBNavGraph.kt               # NavHost, per-tab back stack, mint top bar, workspace drawer
├── features/
│   ├── CalculatorRegistry.kt           # Single registry + CalculatorEngineContract<I, O>
│   ├── onboarding/                     # 4-screen swipeable first-launch walkthrough
│   ├── hub/                            # Home Hub (6 sections, search filter, workspace card)
│   ├── emi/                            # Modular EMI input fields + AdvanceSettingsCard
│   │   └── results/                    # EmiResultsScreen + Yearly, Monthly (LazyColumn), Graph tabs
│   ├── bankstaff/                      # Subsidized Indian Bank Staff Loan calculator
│   ├── mlyoans/                        # MyLoansViewModel (Saved Loans, reminders, DTI)
│   ├── compare/                        # CompareViewModel (2–3 side-by-side offers)
│   ├── profile/                        # ProfileViewModel & ProfileScreen (Settings + Workspace)
│   ├── comingsoon/                     # Shared ComingSoonScreen for upcoming modules
│   └── placeholder/                    # Articles & Market tab placeholders
├── engine/                             # Pure Kotlin financial calculation engine & models
├── data/                               # Room DB, DAO, AppContainer, LoanRepository, DataStore
├── util/                               # PDF/CSV/JSON exporters & AlarmManager EMI reminders
└── ui/
    ├── theme/                          # MoneyWink-inspired M3 Color, Type, Shapes (Light & Dark)
    ├── components/                     # Charts (Donut, Balance Line, Compare Bar) & FintechCard
    └── screens/                        # ExploreScreen (Loan Guide), MyLoansScreen, CompareScreen,
                                        # SettingsScreen, CatalogEditorScreen
```

---

## Add a New Calculator Module in 5 Steps (Example: SIP)

The Hub and Navigation shell are driven by `CalculatorRegistry` and `CalculatorEngineContract<I, O>`:

1. **Create a feature folder:** `app/src/main/java/com/jyotirmoyb/loan/features/sip/`.
2. **Implement the engine contract:** Create `SipViewModel` (or `SipEngine`) implementing `CalculatorEngineContract<SipInput, SipResult>` with immutable `SipUiState` exposed via `StateFlow`.
3. **Create the Compose screen:** Add `SipScreen.kt` using `FintechCard`, `NumericInputField`, and `CurrencyFormatter`.
4. **Enable the module in `CalculatorRegistry.kt`:** Flip `enabled = true` on the `sip` entry and set `route = AppRoute.Sip.route` (adding `data object Sip : AppRoute("sip")` to `AppRoutes.kt`).
5. **Register one route in `LoanJBNavGraph.kt`:** Add `composable(AppRoute.Sip.route) { SipScreen(...) }` inside `NavHost`.

No changes are needed in `HubScreen`, `HubViewModel`, or `MainActivity`.

---

## Editable Loan Catalog (`catalog.json`)

All 6 loan categories (Home, Personal, Auto, Education, Business, Asset-Backed), variant groups, rate ranges, tenure limits, LTV caps, and post-closure checklists live in `app/src/main/assets/catalog.json`.

Users can customize the catalog directly inside **Profile → Edit Loan Catalog** (using either the guided editor or raw JSON mode) or reset back to the bundled seed anytime.

---

## Building & Testing

### Run Unit & Robolectric UI Tests
```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK
```bash
gradle :app:assembleDebug
```

### Verify R8 Shrinking & Build Release APK / AAB
Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true` with tested ProGuard rules in `app/proguard-rules.pro` for Room, Moshi, Coroutines, and `EmiReminderReceiver`.

```bash
# Verify R8 minification without signing
gradle :app:minifyReleaseWithR8

# Build signed Release APK / Play Store AAB (provide your keystore env vars)
KEYSTORE_PATH=/path/to/upload-key.jks \
KEYSTORE_PASSWORD=*** \
KEY_ALIAS=*** \
KEY_PASSWORD=*** \
gradle :app:bundleRelease
```

---

## Disclaimer
Estimates only. Actual bank terms, interest rates, fees, and regulatory limits (including RBI guidelines) may differ.
