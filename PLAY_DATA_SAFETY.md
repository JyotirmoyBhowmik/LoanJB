# Google Play Data Safety Questionnaire — LoanJB (`com.jyotirmoyb.loan`)

**Last Verified:** 01 April 2026  
**Architecture Summary:** 100% Offline-First, On-Device Personal Finance & Loan Planner for India.

---

## 1. Data Collection & Sharing Overview

| Google Play Console Question | Official Answer | Justification & Technical Implementation |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | LoanJB has **zero backend servers**, **zero user accounts**, and **zero analytics/crash/ad SDKs**. Every financial input, saved loan, prepayment log, and profile setting remains strictly inside the app's private on-device sandbox (`Room` SQLite + `DataStore` + `EncryptedSharedPreferences`). |
| **Is all of the user data collected by your app encrypted in transit?** | **N/A (No user data collected)** | Outbound network traffic is limited to anonymous `HTTPS GET` requests to public reference endpoints (AMFI `NAVAll.txt` and public JSON rate configs) only when the user taps refresh. No user parameters, identifiers, or telemetry are sent. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes (On-Device Instant Wipe)** | Users can delete individual saved loans/scenarios at any time, reset all rate overrides under **Profile → Data & Rates**, or clear all app storage from **Profile → Privacy & Security** or Android System Settings. |

---

## 2. Category-by-Category Data Types Checklist

Every category below is marked **Not Collected** and **Not Shared** in Google Play Console:

- **Location** (Approximate / Precise): **Not Collected / Not Shared** (No location permissions declared).
- **Personal Info** (Name, Email, User IDs, Address, Phone): **Not Collected / Not Shared**.
- **Financial Info** (User payment info, Purchase history, Credit score, Other financial info): **Not Collected / Not Shared** — *All EMI calculations, tracked loans, tax simulations, and income figures are stored exclusively on the user's device and never leave the device.*
- **Health and Fitness**: **Not Collected / Not Shared**.
- **Messages** (Emails, SMS, In-app messages): **Not Collected / Not Shared**.
- **Photos and Videos**: **Not Collected / Not Shared**.
- **Audio Files**: **Not Collected / Not Shared**.
- **Files and Docs**: **Not Collected / Not Shared** — *Encrypted JSON backups and PDF/CSV/Excel exports use the zero-permission Android Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`) and `FileProvider` only when explicitly triggered by the user.*
- **Calendar**: **Not Collected / Not Shared**.
- **Contacts**: **Not Collected / Not Shared**.
- **App Activity** (App interactions, In-app search history): **Not Collected / Not Shared**.
- **Web Browsing**: **Not Collected / Not Shared**.
- **App Info and Performance** (Crash logs, Diagnostics): **Not Collected / Not Shared** — *Local crash diagnostics (`GlobalErrorHandler`) are redacted of numbers/PII and kept strictly in private local storage for the user's own inspection.*
- **Device or Other IDs** (Advertising ID, Android ID, IMEI): **Not Collected / Not Shared**.

---

## 3. On-Device Security & Android Auto Backup Safeguards

1. **AES-256-GCM Encrypted Backups:** User-exported `.enc` backup files are encrypted locally using PBKDF2-HMAC-SHA256 (65,536 iterations, random 128-bit salt, random 96-bit IV) and AES-256-GCM authenticated encryption (`BackupCryptoEngine`).
2. **Hardware-Backed Key Storage:** Optional user-supplied market API keys and app-lock PIN hashes are stored in `EncryptedSharedPreferences` backed by Android Keystore (`AES256_GCM`).
3. **Cloud Auto-Backup Exclusion:** `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` explicitly exclude `loanjb_database*` and `loanjb_secure_prefs.xml` from unencrypted Google Drive cloud auto-backups and device-to-device transfers.
4. **Biometric & Screenshot Protection:** Optional `BiometricPrompt` + 4-digit PIN lock and `WindowManager.LayoutParams.FLAG_SECURE` screenshot prevention are available in **Profile → Privacy & Security**.
