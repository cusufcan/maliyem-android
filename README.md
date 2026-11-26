# Maliyem

A simple, private income and expense tracker for Android. Log what comes in and goes out, see the month's net at a glance and where the money went by category, all stored on your device.

Work in progress: this is the project skeleton (theme, database schema, navigation). Screens come next.

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
