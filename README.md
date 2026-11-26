# Maliyem

A simple, private income and expense tracker for Android. Log what comes in and goes out, see the month's net at a glance and where the money went by category, all stored on your device.

## Features

- Expenses and income by category, with monthly summary and category breakdown
- Multiple accounts (wallets) and transfers between them
- Recurring transactions (weekly, monthly, yearly) with reminders
- Search and filters
- Monthly budgets per category and overall, with spending alerts
- Savings goals with deposit and withdrawal tracking
- Six-month trend chart and yearly reports
- Receipt photos attached to transactions
- App shortcuts and a home screen widget
- Optional biometric / device credential app lock
- JSON backup and restore, CSV import and export (files you choose; no network access)

## Stack

- Kotlin, Jetpack Compose (Material 3)
- Room + Kotlin Flow
- Hilt (KSP), Navigation Compose with type-safe routes
- JUnit unit tests
- GitHub Actions CI

Amounts are stored as whole kuruş (`Long`), never floating point.

## Build

Requirements: JDK 17 and the Android SDK (min SDK 26, target SDK 37).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

### Release signing

Release builds are signed only when a `key.properties` file exists in the project root (fields: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). It is git-ignored and never committed; without it, release builds come out unsigned.

```bash
./gradlew bundleRelease        # signed AAB when key.properties is present
```

## Contact

- LinkedIn: [linkedin.com/in/cusufcan](https://linkedin.com/in/cusufcan)
- Email: [yusufcanmercan.info@gmail.com](mailto:yusufcanmercan.info@gmail.com)
