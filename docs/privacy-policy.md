# LoanJB Privacy Policy

**Effective date:** November 2026  
**App package:** `com.jyotirmoyb.loan`

LoanJB is built to work privately and offline on your Android device. This page explains in plain language what data LoanJB uses and what it never collects.

---

## 1. Works Offline — No Account Required
You do not need to sign up, log in, or share an email address, phone number, PAN, Aadhaar, or bank account number to use LoanJB. Every loan, deposit, mutual fund, retirement, and tax calculator works completely offline using built-in configuration files.

## 2. Zero Analytics, Ads, or Tracking
- LoanJB contains **no analytics SDKs**, **no ad networks**, **no crash-telemetry trackers**, and **no third-party data brokers**.
- LoanJB does not collect device identifiers, advertising IDs, location, contacts, or usage profiles.
- Your income figures, loan amounts, EMI schedules, tax inputs, and savings goals **never leave your device**.

## 3. Optional Network Requests (GET-Only Public Data)
Network access is optional and can be turned off anytime under **Profile → Data & Rates**.

When enabled (or when you tap refresh), LoanJB makes **read-only HTTP `GET` requests** strictly to fetch public reference data:
1. Public rate and tax configuration files (`rates.json`, `schemes.json`, `taxslabs.json`, `articles.json`).
2. Public daily mutual fund NAV data from AMFI (`NAVAll.txt`).
3. Optional public market quotes **only** if you explicitly enter your own API key for a supported provider (ExchangeRate-API, MetalpriceAPI, or Twelve Data).

LoanJB never sends your personal financial inputs or saved loans in any network request.

## 4. Local-Only Storage & Encryption
- Saved loans, payment history, rate overrides, imported update bundles, bookmarks, and preferences are stored strictly inside your device's private Android app sandbox (Room database and Jetpack DataStore).
- Optional App Lock PIN hashes (PBKDF2-HMAC-SHA256) and optional user-supplied market API keys are encrypted on-device using **Android Keystore AES-256-GCM**.
- Manual backups that you export are encrypted with a password you choose using **PBKDF2-HMAC-SHA256 (120,000 iterations) + AES-256-GCM**.

## 5. Cloud Backup Excluded
Android Auto Backup and Device-to-Device transfer rules (`backup_rules.xml` and `data_extraction_rules.xml`) explicitly exclude LoanJB's Room database, DataStore preferences, and Android Keystore encrypted preference files so your financial records are not automatically uploaded to cloud backups.

## 6. App Lock & Screen Protection
You can enable **Biometric / PIN App Lock** and **Block Screenshots (`FLAG_SECURE`)** under **Profile → Privacy & Security** to protect your data on shared devices. Biometric checks are handled locally by Android's `BiometricPrompt`; LoanJB never accesses raw biometric data.

## 7. How to Delete Your Data
You are in complete control of your local data:
- **Inside the app:** Go to **Profile → Reset All Local Data** to immediately erase saved loans, overrides, imported bundles, cached NAVs, and preferences.
- **Via Android settings:** Go to **Settings → Apps → LoanJB → Storage → Clear storage**, or uninstall the app to permanently remove all app data from your device.

## 8. Contact
If you have questions about this Privacy Policy or privacy practices in LoanJB, contact:

- **Developer / Support Email:** `TODO: replace-with-your-support-email@example.com`
- **Repository Issues:** `https://github.com/jyotirmoyb/loanjb/issues` *(replace with your repository URL if forked)*
