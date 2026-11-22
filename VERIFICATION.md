# Step365 delivered APK verification

Verified delivery: **22 November 2026**. Native Kotlin/Compose Android application, version **1.0.0 (1)**.

## Installable artifact

- Local build artifact: `Step365-debug.apk`
- Size: **17,447,922 bytes**
- Package: `com.step365.app`
- Minimum: Android 8.0 / API 26; target and compile SDK: Android 16 / API 36
- Verified application label: `Step365`; launcher: `com.step365.app.MainActivity`
- Application source revision: `95946ce55181ef8cff84c3f015b5f423a42372dc` (the following delivery-docs commit does not change application code)
- SHA-256: `c3801e9869bc078de59813a802ca9ea4903e5ec383b1d20e9049367a81989e12`

`apksigner verify --verbose --print-certs` passed, using APK Signature Scheme v2. This is an installable **debug-signed development APK**, not a release build. Its RSA-2048 Android Debug certificate SHA-256 fingerprint is:

`46a7f2cbecb5052e3fe57e414c8d831611c21f9327568faf9621ec82b7875068`

Preserve the local debug keystore for compatible updates. A different CI/debug/release key cannot update this installation in place. Uninstalling to change signing identities can erase all local history.

Additional checks passed: ZIP CRC integrity, required manifest/resources/DEX entries, Android package metadata, and `zipalign -c -P 16 4`. The packaged permissions cover physical activity, notifications, foreground health service, reboot, and AndroidX's private receiver protection. **No internet or location permission is packaged.**

## Executed automated tests

All **56 tests passed**, with **zero failures and zero errors**:

| Suite | Tests | Coverage |
|---|---:|---|
| ChallengeTest | 19 | Dates, estimates, calendar statuses, streaks and statistics |
| CounterReducerTest | 12 | Baselines, duplicates, stale events, resets, reboot and ambiguous dates |
| StepRepositoryTest | 19 | Room persistence/reopen, atomic duplicates, midnight, timezone changes, settings and replacement service sessions |
| NavigationTest | 4 | Real Compose calendar navigation/day selection, statistics, settings save and validation |
| SmokeTest | 2 | Android 15 JVM activity launch without permissions; RemoteViews widget inflation and updates |

Android tests ran under **Robolectric API 35**, not on a physical Android phone. No connected device or emulator was found by `adb devices -l`. Walking accuracy, background collection under actual OEM battery policies, reboot behavior on hardware, and installation/resizing in a real home-screen launcher remain **physical-device tests not performed**. The complete acceptance checklist is in [TESTING.md](TESTING.md).

Android lint completed successfully: **0 errors, 34 warnings, 1 hint**. Remaining warnings concern version updates, layout/style optimizations and optional newer-platform attributes; they were not disabled to conceal errors.

## Build execution and evidence

The final full build succeeded: **58 executed tasks**, including `testDebugUnitTest`, `lintDebug`, and `assembleDebug`. Tools: JDK 21.0.13, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.20, SDK/build-tools 36. The project Gradle wrapper was executed and validated separately.

Windows locked generated files in the OneDrive workspace. The successful clean build kept source in the repository and redirected generated output to a fresh temporary directory with the included init script:

```powershell
$env:STEP365_BUILD_ROOT = Join-Path $env:TEMP 'Step365-build'
.\gradlew.bat --no-daemon --max-workers=1 -I scripts/isolated-build.init.gradle testDebugUnitTest lintDebug assembleDebug '-Pkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false'
```

The original local delivery saved the successful build log, JUnit XML, lint report, signature details, package metadata, and checksum under `deliverables/`. That directory is excluded from Git. The five saved JUnit XML suites were checked during the README review and total 56 tests with zero failures and zero errors.

The results above come from a local build. The subsequent [GitHub Actions run](https://github.com/Gillmasija/Step365/actions/runs/45035099669) failed during Android SDK setup, before tests or APK packaging.

## Device-dependent limits

Tracking requires a compatible sensor, activity permission, and Android allowing the foreground service to continue. The first cumulative reading establishes a baseline; it is never added as today's steps. Service/sensor gaps and ambiguous day boundaries can undercount and are marked incomplete instead of fabricated. Missing-data days below the goal remain gray. Clearing app data or uninstalling can erase history. Widget refresh is event-driven and throttled, with Android-controlled scheduling rather than guaranteed second-by-second updates.
