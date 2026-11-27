# LoanJB — Production Release & Play Store Checklist (`RELEASE.md`)

## 1. Single-Source Version Management
Version numbers are managed in a single place: **`gradle/libs.versions.toml`**:

```toml
[versions]
appVersionCode = "2"
appVersionName = "2.1.0"
```

`app/build.gradle.kts` reads `libs.versions.appVersionCode` and `libs.versions.appVersionName` and exposes them via `BuildConfig.VERSION_CODE` and `BuildConfig.VERSION_NAME`.

- [ ] Increment `appVersionCode` (monotonically increasing integer) before every Google Play upload.
- [ ] Update `appVersionName` (semantic version `MAJOR.MINOR.PATCH`).
- [ ] Add a concise release entry in `CHANGELOG.md`.

---

## 2. Data Verification Before Each Release

Before tagging or building a production release, verify every financial rate, tax rule, and scheme parameter against official Indian sources and confirm `remote-config/` and `app/src/main/assets/remote-config/` are byte-for-byte identical.

### Official Sources to Verify
| Domain | Files | Official Source |
| :--- | :--- | :--- |
| **RBI Policy Rates** (Repo, SDF, MSF, Bank Rate, CRR, SLR) | `rates.json` | [RBI Press Releases & Current Rates](https://www.rbi.org.in/) (after every bi-monthly MPC statement) |
| **Bank FD Reference Rates** (SBI, HDFC, ICICI, Axis, Kotak, Post Office) | `rates.json` | Official bank retail domestic FD rate schedules (general & senior citizen slabs) |
| **Small Savings Schemes** (PPF, SSY, SCSS, NSC, KVP, PO MIS, PO TD, PO RD) | `schemes.json` | [DEA / Ministry of Finance Quarterly Circulars](https://dea.gov.in/) & [India Post](https://www.indiapost.gov.in/) (published before Apr 1, Jul 1, Oct 1, Jan 1) |
| **EPF & EPS** (EPF rate, ₹15,000 wage ceiling, ₹1,250 EPS cap) | `schemes.json` | [EPFO Circulars](https://www.epfindia.gov.in/) |
| **NPS, APY, PM-SYM & Senior Pension Schemes** | `schemes.json` | [PFRDA](https://www.pfrda.org.in/) & [Labour Ministry PM-SYM](https://maandhan.in/) |
| **Income Tax Slabs, Sec 87A Rebate, Surcharge, Cess, HRA, Advance Tax, GST & MF Capital Gains** | `taxslabs.json` | [Income Tax Department India](https://www.incometaxindia.gov.in/) & active Union Budget / Finance Act |
| **Mutual Fund NAVs** | Live on-device sync | [AMFI India NAVAll.txt](https://portal.amfiindia.com/spages/NAVAll.txt) (fetched on demand by user) |

### Update-Brief Workflow (`LoanJB_Data_Update_Brief.md`)
1. Generate or review the quarterly/post-Budget update bundle using the schema in `remote-config/schema.json` and the prompt brief (`LoanJB_Data_Update_Brief.md` / in-app **Profile → Data & Rates → Import update file**).
2. Ensure **every changed or verified item** carries:
   - `effectiveFrom` in `YYYY-MM-DD` format
   - `lastVerified` in `YYYY-MM-DD` format (never `"UNVERIFIED"` for official release values)
   - An official `https://` `sourceUrl` and clear `sourceName`
3. Import and preview the bundle inside a debug build (**Profile → Data & Rates → Import update file**) to visually audit every `old → new` diff and verify zero unknown keys or unit mismatches.
4. Update the canonical files in `remote-config/` (`rates.json`, `schemes.json`, `taxslabs.json`, `articles.json`) and copy them identically into `app/src/main/assets/remote-config/`.
5. Confirm exact sync:
   ```bash
   for f in rates.json schemes.json taxslabs.json articles.json; do
     diff -u "remote-config/$f" "app/src/main/assets/remote-config/$f"
   done
   ```
6. Confirm **Room Database Schema (`app/schemas/com.jyotirmoyb.loan.data.LoanDatabase/`)**: if any `@Entity` changed, increment `LoanDatabase` version, add an explicit `Migration(N, N+1)` to `LoanDatabase.ALL_MIGRATIONS`, export `<version>.json`, and pass `LoanDatabaseMigrationTest` & `RoomAllMigrationsInstrumentedTest`.

---

## 3. Gradle Wrapper & Automated Quality Gates

All local and CI builds use the pinned Gradle wrapper (`./gradlew`, Gradle 9.3.1 for AGP 9.1.1). To regenerate wrapper files if needed:

```bash
gradle wrapper --gradle-version 9.3.1
```

Run the full pre-release verification suite:

```bash
# 1. Static analysis (ktlint against baseline — fails on any new issue)
./gradlew :app:ktlintCheck --console=plain

# 2. Unit, Independent Tax, Calculation Cross-Check, Security Migration, Room Migration & Robolectric UI Tests
./gradlew :app:testDebugUnitTest --console=plain

# 3. JaCoCo Coverage Report & Domain >= 80% Coverage Gate
./gradlew :app:jacocoTestReport :app:verifyDomainCoverage --console=plain

# 4. Android Lint (Debug + Release with warnings-as-errors)
./gradlew :app:lintDebug :app:lintRelease --console=plain

# 5. Debug APK + Instrumentation Test APK
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain

# 6. Connected Instrumentation Tests (requires attached Android device or emulator)
./gradlew :app:connectedDebugAndroidTest --console=plain

# 7. Release R8 Smoke Test on Emulator (after assembling release APK)
bash scripts/release-smoke-test.sh app/build/outputs/apk/release/app-release.apk
```

- [ ] `ktlintCheck` passes with 0 new issues.
- [ ] `testDebugUnitTest` passes with 0 failures (including `IndependentTaxKnownAnswerTest`, `CalculationCrossCheckTest`, `UpdateBundleImportTest`, `LoanDatabaseMigrationTest`, `HardcodedStringsGuardTest`).
- [ ] `verifyDomainCoverage` passes (≥ 80% line coverage on `domain/`).
- [ ] `lintDebug` and `lintRelease` pass cleanly.
- [ ] `connectedDebugAndroidTest` and `scripts/release-smoke-test.sh` pass on an Android emulator/device.
- [ ] `app/src/main/baseline-prof.txt` is present and packaged via `androidx.profileinstaller`.

---

## 4. Creating an Upload Keystore Locally & Building the Signed AAB

> **CRITICAL SECURITY RULE:** Generate your upload keystore **only on your local machine or secure offline vault**. **NEVER commit `.jks` / `.keystore` files, passwords, or `.env` files to Git.** Do **not** modify the `signingConfigs` block in `app/build.gradle.kts`.

### Step A — Create a Local Upload Key (One-Time Setup)
`app/build.gradle.kts` expects key alias **`upload`**:

```bash
mkdir -p ~/.loanjb-keys
keytool -genkeypair -v \
  -keystore ~/.loanjb-keys/loanjb-upload-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias upload
```

Store the keystore file and both passwords in a password manager / encrypted backup immediately.

### Step B — Export Environment Variables & Build Signed Production AAB
```bash
export KEYSTORE_PATH="$HOME/.loanjb-keys/loanjb-upload-key.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

./gradlew :app:bundleRelease --console=plain
```

Output AAB location:
- `app/build/outputs/bundle/release/app-release.aab`

Optional signed APK (for local sideloading / emulator smoke test):
```bash
./gradlew :app:assembleRelease --console=plain
# Output: app/build/outputs/apk/release/app-release.apk
```

### Step C — Verify AAB Signature & Unset Shell Secrets
```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
unset KEYSTORE_PATH STORE_PASSWORD KEY_PASSWORD
```

### Step D — Google Play App Signing
1. When creating the app in Google Play Console, enable **Play App Signing** (required for all new Android App Bundles).
2. Google Play generates and protects the **App Signing Key**; the keystore created in Step A acts strictly as your **Upload Key**.
3. If your upload key is ever lost or compromised, you can request an Upload Key Reset inside **Play Console → Setup → App signing** without losing the ability to update existing users.
4. Keep the R8 mapping file (`app/build/outputs/mapping/release/mapping.txt`) uploaded alongside every release AAB (automatically bundled or uploaded in Play Console → App bundle explorer) for readable stack traces.

---

## 5. Google Play Console Store Listing Assets & Declarations

### Store Listing Assets Checklist
- [ ] **App Title (≤ 30 chars):** `LoanJB: EMI & Tax Planner` (EN-IN & HI-IN copy in `play-store-assets/store-listing-en-IN.md` and `store-listing-hi-IN.md`).
- [ ] **Short Description (≤ 80 chars)** & **Full Description (≤ 4,000 chars)** in English (`en-IN`) and Hindi (`hi-IN`).
- [ ] **High-res App Icon:** `512 × 512 px` PNG (32-bit, alpha allowed).
- [ ] **Feature Graphic:** `1024 × 500 px` PNG/JPEG (no sensitive text near edges).
- [ ] **Phone Screenshots:** At least 4–8 screenshots (`16:9` or `9:16`, min 320px, max 3840px) covering Calculator Hub, Loan EMI + Amortization Schedule, Income Tax New vs Old Comparison, Small Savings / SIP, and Data & Rates Verification.
- [ ] **Tablet / 7-inch & 10-inch Screenshots (recommended):** Show NavigationRail adaptive layout.
- [ ] **Adaptive & Monochrome Icon:** Verified `ic_launcher.xml`, `ic_launcher_round.xml`, and `@drawable/ic_launcher_monochrome` on Android 13+ themed launchers.
- [ ] **Splash Screen:** Verified Android 12+ `installSplashScreen()` cold-start transition.

### Permissions & Financial Services Declaration
- [ ] **Permissions:** Only `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, optional runtime `POST_NOTIFICATIONS` (EMI reminders), and `USE_BIOMETRIC` (optional local App Lock). Zero location, camera, contacts, SMS, or Advertising ID permissions.
- [ ] **Financial Features Declaration:** LoanJB is strictly an **offline educational financial calculator and personal EMI tracker** — it does **not** offer, originate, broker, or facilitate personal loans, credit lines, BNPL, or investment transactions. Select *"My app doesn't provide any financial features"* / *"Financial calculator & personal tracker (non-lending)"* as prompted in Play Console.
- [ ] **License:** Replace any placeholder `TODO` in root `LICENSE` before publishing the repository publicly.

### Content Rating (IARC)
- [ ] Complete the IARC questionnaire under **Policy and programmes → App content → Content rating**.
- [ ] Select category **Utility, Productivity, Communication, or Other**.
- [ ] Answer **No** to violence, sexual content, profanity, controlled substances, gambling, user-generated content sharing, and location sharing.
- [ ] Expected rating: **Everyone (ESRB) / PEGI 3 / 3+**.

### Target Audience & Families Policy
- [ ] Open **App content → Target audience and content**.
- [ ] Select **18 and over** (adult personal finance, loan EMI, and Indian income tax planning utility).
- [ ] Explicitly indicate the app is **NOT directed at children** and does not appeal primarily to children under 13 (avoids Families Policy certification overhead while staying accurate to the audience).

---

## 6. Data Safety Questionnaire Answers (Copied from `PLAY_DATA_SAFETY.md`)

Use these exact answers in **Play Console → Policy and programmes → App content → Data safety**:

| Google Play Console Question | Official Answer | Justification |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | LoanJB has zero backend servers, zero user accounts, and zero analytics/crash/ad SDKs. All loans, calculations, and preferences stay strictly in the on-device sandbox (`Room` SQLite + `DataStore` + Android Keystore AES-256-GCM preferences). |
| **Is all of the user data collected by your app encrypted in transit?** | **N/A (No user data collected)** | Outbound network calls are limited to anonymous `HTTPS GET` requests to public reference feeds (`rates.json`, `taxslabs.json`, `schemes.json`, `articles.json`, AMFI `NAVAll.txt`) and optional user-keyed market APIs. Zero user financial inputs or identifiers leave the device. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes (Instant On-Device Wipe)** | Users can delete individual saved loans, reset overrides in **Profile → Data & Rates**, or wipe all app data from **Profile → Settings** / Android Settings. |

**Category-by-category answers:** Mark **Not Collected** and **Not Shared** for all 14 categories:
- Location, Personal Info, Financial Info *(stored strictly on-device)*, Health & Fitness, Messages, Photos & Videos, Audio Files, Files & Docs *(manual user-triggered SAF exports/imports only)*, Calendar, Contacts, App Activity, Web Browsing, App Info & Performance *(local redacted crash log only)*, Device or Other IDs *(no AD_ID permission)*.

**On-device security safeguards:**
- Manual JSON backups encrypted with PBKDF2-HMAC-SHA256 + AES-256-GCM (`BackupCryptoEngine`).
- Optional App Lock PIN hashes and optional Market API keys encrypted with Android Keystore AES-256-GCM (`KeystoreEncryptedPrefs`).
- `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` exclude `loanjb_offline.db*` and Keystore encrypted preference files from cloud Auto Backup and device-to-device transfers.
- Optional `BiometricPrompt` + PIN lock and `FLAG_SECURE` screenshot protection in **Profile → Privacy & Security**.

---

## 7. Pre-Release Testing Tracks & Rollout Plan

1. **Internal Testing Track (Day 1–2):**
   - Upload signed `app-release.aab` to **Testing → Internal testing** (up to 100 testers; installs within minutes).
   - Verify on at least 2 physical devices (light/dark mode, English + Hindi, 200% font scale, R8 release build):
     - Fresh install onboarding → Hub → Loan (EMI) → Yearly/Monthly/Graph → Save Loan → My Loans.
     - Upgrade install over previous versionCode (verifies Room migration preserves existing saved loans & Keystore migration preserves PIN/API keys).
     - Import update bundle (preview, apply, undo), PDF/CSV export, encrypted backup/restore, EMI reminder notification, home-screen widget.
2. **Closed Testing Track (Minimum 14 days for personal Play accounts):**
   - Promote the verified AAB to **Testing → Closed testing** (invite ≥ 12–20 testers for personal developer accounts as required by Google Play).
   - Monitor Play Console **Android Vitals** for crash-free sessions (target ≥ 99.9%) and zero ANRs.
3. **Production Staged Rollout:**
   - Promote to **Production** starting at **20% staged rollout**, monitor Android Vitals for 48 hours, then expand to **50% → 100%**.

---

## 8. Publishing `docs/` on GitHub Pages (Privacy Policy & Terms URL)

Google Play requires a publicly accessible HTTPS Privacy Policy URL. LoanJB stores its plain-language policy and terms in `docs/privacy-policy.md` and `docs/terms.md`.

1. Replace the `TODO` support email in `docs/privacy-policy.md`, and update `PRIVACY_POLICY_WEB_URL` / `TERMS_OF_USE_WEB_URL` in `AboutAndLegalScreen.kt` if your GitHub repo URL differs.
2. Push `docs/` to `main`, open GitHub → **Settings → Pages** → **Deploy from a branch** → Branch **`main`**, Folder **`/docs`**, click **Save**.
3. Verify both HTTPS URLs load:
   - `https://<your-github-username>.github.io/<repo-name>/privacy-policy`
   - `https://<your-github-username>.github.io/<repo-name>/terms`
4. Paste the Privacy Policy URL into **Play Console → Policy and programmes → App content → Privacy policy**.
