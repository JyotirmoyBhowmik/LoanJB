# LoanJB — Production Release & Verification Checklist (`RELEASE.md`)

## 1. Single-Source Version Management
Version numbers are managed in a single place: **`gradle/libs.versions.toml`**:

```toml
[versions]
appVersionCode = "1"
appVersionName = "1.0.0"
```

`app/build.gradle.kts` reads `libs.versions.appVersionCode` and `libs.versions.appVersionName` and exposes them via `BuildConfig.VERSION_CODE` and `BuildConfig.VERSION_NAME`.

- [ ] Increment `appVersionCode` (monotonically increasing integer) in `gradle/libs.versions.toml` before every Play Console upload.
- [ ] Update `appVersionName` (semantic version `MAJOR.MINOR.PATCH`) in `gradle/libs.versions.toml`.

---

## 2. Pre-Release Financial Data & Schema Audit
- [ ] **`app/src/main/assets/rates.json`**: Confirm `asOf` date, `lastVerified` date, RBI Repo Rate, Benchmark FD/Loan bands, and Gold/Silver reference values match official RBI/market releases.
- [ ] **`app/src/main/assets/schemes.json`**: Confirm quarterly DEA / India Post Small Savings notification rates (PPF, SSY, SCSS, NSC, KVP, POMIS, MSSC, PMVVY status).
- [ ] **`app/src/main/assets/taxslabs.json`**: Confirm CBDT Finance Act slabs, Section 87A rebate thresholds, marginal relief, surcharge caps, and 4% Health & Education Cess for the active Assessment Year.
- [ ] **Room Database Schema (`app/schemas/com.jyotirmoyb.loan.data.LoanDatabase/`)**: Verify that any entity change increments `LoanDatabase` version, adds an explicit `Migration(N, N+1)` to `LoanDatabase.ALL_MIGRATIONS`, exports the new `<version>.json` schema, and passes `LoanDatabaseMigrationTest`.

---

## 3. Automated Quality, Coverage & R8 Verification
Run each verification step sequentially:

```bash
# 1. Assemble Debug & Export Room KSP Schema
gradle :app:assembleDebug --console=plain

# 2. Run Unit, Robolectric Compose UI, Room Migration & Performance Benchmark Tests
gradle :app:testDebugUnitTest --console=plain

# 3. Generate JaCoCo Code Coverage Report (HTML + XML)
gradle :app:jacocoTestReport --console=plain

# 4. Verify Release R8 Shrinking, Obfuscation & ProGuard Rules
gradle :app:minifyReleaseWithR8 --console=plain
```

- [ ] All unit tests (EMI, Flat vs Reducing, Prepayment/Rate-Change schedule recomputation, SIP/SWP/XIRR, Tax Slabs, Retirement/NPS/EPF/APY, AES-256-GCM Backup, Room 1->5 Migrations, Cold Start & 360-row Schedule Benchmark, and Compose UI tests) pass with 0 failures.
- [ ] `minifyReleaseWithR8` succeeds without missing class warnings for Room, Moshi, Retrofit, WorkManager, Glance/AppWidget, or `EncryptedSharedPreferences`.
- [ ] `app/src/main/baseline-prof.txt` is present and packaged via `androidx.profileinstaller`.

---

## 4. Building a Signed Release AAB / APK Using Environment Variables

> **IMPORTANT:** Do **not** hardcode keystore passwords in source files or modify the existing `signingConfigs` block in `app/build.gradle.kts`.

The project's `signingConfigs` in `app/build.gradle.kts` reads credentials directly from environment variables:
- `KEYSTORE_PATH`: Path to your production upload `.jks` or `.keystore` file (defaults to `debug.keystore` when unset).
- `STORE_PASSWORD`: Keystore password.
- `KEY_PASSWORD`: Key alias password.

### Build Signed Production Android App Bundle (`.aab`)
```bash
export KEYSTORE_PATH="/secure/path/to/loanjb-upload-keystore.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

gradle :app:bundleRelease --console=plain
```
Output AAB location:
`app/build/outputs/bundle/release/app-release.aab`

### Build Signed Production APK (`.apk`)
```bash
gradle :app:assembleRelease --console=plain
```
Output APK location:
`app/build/outputs/apk/release/app-release.apk`

### Verify AAB Signature
```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

---

## 5. Google Play Console Submission Checklist
- [ ] **App Title (≤30 chars):** `LoanJB: EMI & Tax Planner` (see `play-store-assets/store-listing-en-IN.md` and `store-listing-hi-IN.md`).
- [ ] **Adaptive & Monochrome Icon:** Verified `ic_launcher.xml`, `ic_launcher_round.xml`, and `@drawable/ic_launcher_monochrome` on Android 13+ themed icon launchers.
- [ ] **Splash Screen:** Verified Android 12+ `installSplashScreen()` cold-start transition.
- [ ] **Data Safety Form:** Completed using `PLAY_DATA_SAFETY.md` (**No data collected**, **No data shared**).
- [ ] **Permissions Declaration:** Only `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, and optional runtime `POST_NOTIFICATIONS` (for user-enabled EMI due alerts) + `USE_BIOMETRIC` (for optional on-device app lock). Zero storage, location, camera, or advertising ID permissions.
- [ ] **Financial Services Compliance:** LoanJB is strictly an **offline educational financial calculator and personal EMI tracker** — it does **not** offer, originate, broker, or facilitate personal loans, credit lines, or investment products. Select *"Financial calculator / personal budget & tracker (non-lending)"* in Play Console declarations.
