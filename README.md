# LoanJB — Offline Multi-Category Loan Planner & Tracker (`com.jyotirmoyb.loan`)

LoanJB is a 100% offline Android app built with **Kotlin, Jetpack Compose, Material 3, Room, and DataStore**. It supports 6 comprehensive loan categories, multi-variant calculation rules, interactive amortization charts/tables, CSV/PDF export, side-by-side comparison, local EMI due reminders, and a fully editable JSON loan catalog.

---

## Adding or Editing a Loan Category / Variant

All loan categories, variant groups, rate ranges, tenure limits, LTV caps (including editable Nepal Rastra Bank EV vs Fuel limits), fees, and post-closure checklists live in a single JSON catalog:

- **Seed file in source:** `app/src/main/assets/catalog.json`
- **In-app editor:** Open **Settings → Edit Loan Catalog** to use either the **Visual Editor** (tweak rates/tenures/LTV or add new categories & variants) or the **Raw JSON** tab. Edits are persisted offline in Room, and you can reset to the seed anytime.

### Variant JSON Structure

```json
{
  "id": "home_step_up_custom",
  "name": "Custom Step-Up EMI",
  "howItWorks": "Explanation shown in Explore detail sheet.",
  "bestFor": "Target borrower profile.",
  "ruleFlag": "EMI_STEP_UP",
  "rateRangeMin": 8.0,
  "rateRangeMax": 11.5,
  "typicalTenureLabel": "10 - 25 yrs",
  "prepaymentFeePercent": 0.0,
  "ltvCapOverride": 80
}
```

### Supported `ruleFlag` Values
- **Rates:** `RATE_FIXED`, `RATE_FLOATING`, `RATE_HYBRID`, `BASIS_REDUCING`, `BASIS_FLAT_COMPARE`
- **EMI Structure:** `EMI_STANDARD`, `EMI_STEP_UP`, `EMI_STEP_DOWN`, `EMI_OFFSET`, `EMI_BALLOON`
- **Disbursement & Moratorium:** `DISB_FULL`, `DISB_TRANCHE_PRE_EMI`, `TERM_MORATORIUM`, `MOR_SIMPLE`, `MOR_COMPOUND`
- **Purpose & Facilities:** `PURPOSE_PURCHASE`, `PURPOSE_RENOVATION`, `PURPOSE_TOP_UP`, `PURPOSE_BALANCE_TRANSFER`, `FAC_TERM`, `FAC_FLEXI_LOC`, `FAC_OVERDRAFT`, `BIZ_TERM`, `BIZ_CASH_CREDIT`, `BIZ_BILL_DISCOUNT`, `BIZ_LC_TR`
- **Assets & Vehicles:** `VEH_EV`, `VEH_FUEL`, `LTV_HIGH_FINANCING`, `ASSET_FD`, `ASSET_GOLD_EMI`, `ASSET_GOLD_INTEREST_ONLY`, `ASSET_GOLD_BULLET`, `ASSET_MARGIN_SHARE`

---

## Building the Android APK & Running Unit Tests

1. **Run calculation engine unit tests (JVM):**
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. **Build a Debug APK:**
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`
3. **Build a Release APK / AAB:**
   ```bash
   gradle :app:assembleRelease
   gradle :app:bundleRelease
   ```
   *(Configure `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables for release signing.)*
