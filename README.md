# Maliyem

A simple, private income and expense tracker for Android. Log what comes in and goes out, see the month's net at a glance and where the money went by category, all stored on your device.

[![Get it on Google Play](https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg)](https://play.google.com/store/apps/details?id=com.mercan.maliyem)

[![CI](https://github.com/cusufcan/maliyem-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/cusufcan/maliyem-android/actions/workflows/ci.yml)

## Features

- Expenses and income by category, with a monthly summary and category breakdown
- Multiple accounts (wallets) and transfers between them
- Recurring transactions (weekly, monthly, yearly) with reminders
- Search and filters
- Monthly budgets per category and overall, with spending alerts
- Savings goals with deposit and withdrawal tracking
- Six-month trend chart and yearly reports
- Receipt photos with on-device text recognition (amount, date, merchant)
- App shortcuts and a home screen widget
- Optional biometric / device credential app lock
- JSON backup and restore, CSV import and export
- English and Turkish, light and dark theme

No account, ads or analytics, and no internet permission: receipt text is read by a model bundled with the app, and backups go only to files you choose. See the [privacy policy](https://cusufcan.github.io/maliyem/privacy.html).

## Stack

- Kotlin, Jetpack Compose (Material 3)
- MVVM with unidirectional data flow
- Room + Kotlin Flow, with migration tests for every schema version
- Hilt (KSP), Navigation Compose with type-safe routes
- WorkManager for recurring entries and reminders, Glance for the widget
- ML Kit text recognition (bundled Latin model)
- JUnit and coroutines-test unit tests, instrumented tests for migrations and OCR
- GitHub Actions CI (unit tests, lint, build)

Amounts are stored as whole kuruş (`Long`), never floating point.

## Build

Requirements: JDK 17 and the Android SDK (min SDK 26, target SDK 37).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
./gradlew connectedAndroidTest # instrumented tests (emulator or device)
```

### Release signing

Release builds are signed only when a `key.properties` file exists in the project root (fields: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). It is git-ignored and never committed; without it, release builds come out unsigned.

```bash
./gradlew bundleRelease        # signed AAB when key.properties is present
```

## Credits

Built in collaboration with AI coding agents such as Claude and Codex.

## License

Copyright © 2026 Yusuf Can Mercan. All rights reserved.

The source code is published for viewing only. Copying, modifying, redistributing or using it for commercial purposes requires written permission. See [LICENSE](LICENSE).

## Contact

- LinkedIn: [linkedin.com/in/cusufcan](https://linkedin.com/in/cusufcan)
- Email: [yusufcanmercan.info@gmail.com](mailto:yusufcanmercan.info@gmail.com)
