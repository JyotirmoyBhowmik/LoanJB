#!/usr/bin/env bash
set -euo pipefail

# LoanJB Release R8 Emulator Smoke Test
# Installs the R8-minified release APK onto a running Android emulator/device,
# launches MainActivity, triggers widget/app flows, and checks logcat for any
# R8 stripping crash (FATAL EXCEPTION, ClassNotFoundException, NoSuchMethodError,
# VerifyError, or Moshi/Room/WorkManager runtime failures).

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"
PKG="com.jyotirmoyb.loan"
ACTIVITY="${PKG}/.MainActivity"

if [ ! -f "${APK_PATH}" ]; then
  echo "ERROR: Release APK not found at ${APK_PATH}"
  echo "Build it first with KEYSTORE_PATH=... STORE_PASSWORD=... KEY_PASSWORD=... ./gradlew :app:assembleRelease"
  exit 1
fi

echo "==> Checking attached Android device/emulator..."
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'

echo "==> Unlocking emulator screen and disabling animations..."
adb shell input keyevent 82 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

echo "==> Installing R8 release APK: ${APK_PATH}"
adb uninstall "${PKG}" >/dev/null 2>&1 || true
adb install -r -g "${APK_PATH}"

echo "==> Clearing logcat buffer..."
adb logcat -c

echo "==> Launching ${ACTIVITY} (exercises Room, Moshi, WorkManager, Keystore, Compose)..."
adb shell am start -W -n "${ACTIVITY}"
sleep 4

echo "==> Verifying app process is alive after launch..."
PID="$(adb shell pidof "${PKG}" | tr -d '\r')"
if [ -z "${PID}" ]; then
  echo "ERROR: ${PKG} process exited immediately after launch!"
  adb logcat -d -s AndroidRuntime:E
  exit 1
fi
echo "Running PID: ${PID}"

echo "==> Exercising main UI flow via light monkey events (onboarding -> Hub -> calculators)..."
adb shell monkey -p "${PKG}" --pct-touch 60 --pct-motion 20 --pct-nav 10 --pct-majornav 10 --throttle 150 40 >/dev/null 2>&1 || true
sleep 2

echo "==> Triggering AppWidget broadcast to verify R8 kept widget provider & Room access..."
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n "${PKG}/.widget.EmiCountdownWidgetProvider" >/dev/null 2>&1 || true
sleep 2

echo "==> Re-verifying ${PKG} process is still alive..."
PID_AFTER="$(adb shell pidof "${PKG}" | tr -d '\r')"
if [ -z "${PID_AFTER}" ]; then
  echo "ERROR: ${PKG} crashed during smoke flow!"
  adb logcat -d -s AndroidRuntime:E
  exit 1
fi

echo "==> Auditing logcat for fatal crashes or R8 stripping errors..."
CRASH_LOG="$(adb logcat -d | grep -E "FATAL EXCEPTION|ClassNotFoundException.*jyotirmoyb|NoSuchMethodError.*jyotirmoyb|NoSuchFieldError.*jyotirmoyb|VerifyError.*jyotirmoyb|Unable to instantiate|JsonDataException" || true)"
if [ -n "${CRASH_LOG}" ]; then
  echo "ERROR: Detected fatal or R8 stripping error in logcat:"
  echo "${CRASH_LOG}"
  exit 1
fi

echo "==> PASS: R8 Release smoke test completed cleanly with 0 crashes!"
