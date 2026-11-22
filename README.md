# Step365

A native, offline Android step tracker for a fixed 365-day challenge: **22 November 2026 through 21 November 2027**, inclusive. Complete **10,000 recorded steps per day**, for a challenge target of **3,650,000 steps**. The challenge never restarts automatically.

Step365 uses Kotlin, Jetpack Compose and Material 3, Room, Android step sensors, a health foreground service, and a resizable RemoteViews home-screen widget. It supports Android 8.0 (API 26) and later, including Android 15, and targets API 36. It has no account, advertisements, subscription, GPS collection, or runtime internet permission.

## Install on your phone

1. Download **Step365-debug.apk** from the delivered artifact onto your phone. If using GitHub Actions, open a successful **Android APK** run, download the **Step365-debug** artifact, and extract its ZIP to find the APK.
2. Open the APK in your browser or file manager. If Android requests it, allow **Install unknown apps** for that specific source. You can turn that permission off again afterward.
3. Install **Step365** and open it.
4. Tap **Allow physical activity**, grant permission, then tap **Start tracking**. In Settings, use the notification switch to allow notifications if you want to see tracking in the notification drawer. Android still requires a foreground-service notification even if notification permission is denied.
5. Keep your phone with you while walking. Confirm that the dashboard reports an available sensor and active tracking. A device without a compatible sensor displays an explanation and does not invent steps.
6. To add the widget, long-press an empty area of your home screen, choose **Widgets**, find **Step365**, and drag the widget onto the home screen. Resize it using your launcher's handles. Tap it to open the app.
7. If background collection stops, open Step365's settings and review the phone's app battery settings. Manufacturer labels vary; permit background activity or select an unrestricted battery setting if you want ongoing collection. Android and the phone manufacturer retain control over background execution.
8. Open **Calendar** to review daily progress and tap a date for its recorded steps, estimated distance, estimated activity calories, and completion status.

The app can collect steps before and after the challenge; challenge statistics only include the configured challenge dates. The displayed daily total follows the phone's local date.

## What the data means

- The **original challenge goal remains 10,000 steps**. Changing the personal daily goal affects personal daily progress and the widget, without changing challenge scoring or historical challenge achievements.
- **Green** means a finished challenge day has at least 10,000 recorded steps. **Red** means a finished day has reliable recorded coverage and fewer than 10,000 steps. **Purple** identifies today and its progress. **Gray** covers future dates and dates without sufficient recording. Dates outside the challenge are identified separately. Today is never marked missed before it ends.
- Reaching 10,000 observed steps establishes completion even if some recording was unavailable. A partial low total cannot establish failure and remains missing-data status.
- Current streak includes today if today's challenge goal is complete; otherwise yesterday's streak remains available until today ends. An ended missed or missing day breaks a consecutive completion streak. Previously completed days remain saved.
- Completion rate uses completed **finished** days divided by all finished challenge days, including missing-data days. Missing days are also reported separately. Average steps uses elapsed challenge days, including today and missing-data days. Time progress is finished challenge days divided by 365; remaining days includes today while it can still be completed.

Distance is an estimate: `steps × stride length in meters ÷ 1000`. Default stride is **0.70 m**, adjustable in Settings. Kilometers are displayed to two decimal places.

Estimated **activity** calories use `0.5 × body weight in kilograms × estimated kilometers`, a simple approximation for net energy used during level walking, excluding resting energy. Weight is optional; calories remain unavailable until weight is supplied. Terrain, pace, gait, and individual physiology affect actual expenditure. These estimates are not medical measurements. Changing stride or weight recalculates estimates from stored steps.

## Tracking and privacy limits

The preferred sensor is `TYPE_STEP_COUNTER`; its lifetime cumulative reading is used only as a baseline, never credited as today's total. `TYPE_STEP_DETECTOR` is a fallback when the counter is unavailable. Sensor checkpoints and daily records prevent duplicate credit across ordinary restarts. Database writes are transactional. Midnight, reboot, counter reset, and clock/time-zone changes are treated conservatively so uncertain steps are not fabricated or silently assigned to the wrong day.

Tracking runs in a visible health foreground service when Android permits it. Step events drive database and widget updates. Widgets are subject to launcher/platform scheduling, with a 30-minute scheduled fallback; updates are not guaranteed every second. Battery restrictions, permission revocation, force stop, process termination, device shutdown, or sensor unavailability can interrupt recording. Reboot recovery attempts to restart a previously enabled health tracking service when platform rules permit it. **After a force stop, reopen the app to resume tracking.**

Step365 has no historical health-data integration. It cannot promise recovery of steps taken while tracking was unavailable. The first sensor sample establishes a baseline, and ambiguous intervals are marked incomplete. Small gaps or day-boundary intervals may therefore undercount rather than create misleading totals.

History and preferences remain on the device. No cloud account or analytics is used, and Android application backup is disabled. **Clearing app data or uninstalling can erase all history and settings.** Keep this in mind before replacing an APK that Android cannot update in place.

## Build the Android Studio project

Install JDK 21 and an Android SDK containing **platform 36**, **build-tools 36.0.0**, and platform-tools. Open this directory in Android Studio, or set `JAVA_HOME` and `ANDROID_HOME` for command-line builds. An Android Studio-generated `local.properties` can specify the SDK path instead. The Gradle wrapper downloads the pinned Gradle distribution, and the first build downloads dependencies.

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS/Linux:

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. Copy it as `Step365-debug.apk` for delivery. The `.github/workflows/android.yml` workflow performs the same verification on push, pull request, or manual dispatch and uploads a downloadable APK plus available verification reports. A configured workflow alone is not proof that a build ran; consult its run result and the delivered verification notes.

If Windows or a synced folder locks generated files, keep sources here and use an isolated output directory:

```powershell
$env:STEP365_BUILD_ROOT = Join-Path $env:TEMP 'Step365-build'
.\gradlew.bat --no-daemon --max-workers=1 -I scripts/isolated-build.init.gradle testDebugUnitTest lintDebug assembleDebug
```

The APK then appears under `$env:STEP365_BUILD_ROOT/app/outputs/apk/debug/`. See [VERIFICATION.md](VERIFICATION.md) for the exact delivered artifact and executed checks.

## Signing and future updates

The development APK is debug-signed and installable outside Google Play. It is not a production release-signed APK. An in-place update must use the same application ID, a compatible version code, and **the same signing identity**. Preserve the local debug keystore used for this delivery, or use a dedicated private release key for future production builds. Never commit a private signing key to the repository.

Fresh GitHub Actions runners normally generate a new debug key. Their APKs may therefore be incompatible with an existing installation's signing identity. An uninstall/reinstall can resolve that conflict but erases local history. A stable private signing key supplied securely to CI is required before treating CI artifacts as reliable in-place updates.

## Architecture and verification

The `domain` package contains challenge dates, estimates, streak/statistics rules, and counter reduction. The `data` package owns Room persistence, checkpoints, and local preferences. The tracking service integrates Android sensor and foreground-service lifecycles. Compose screens observe persisted state through lifecycle-aware state collection. The widget reads the same repository rather than keeping a separate step total. Schema exports are versioned; future database changes require explicit migrations rather than destructive resets.

See [TESTING.md](TESTING.md) for the automated verification commands and physical-device acceptance checklist. Device walking, battery behavior, and launcher behavior must be distinguished from JVM tests and emulator smoke checks.
