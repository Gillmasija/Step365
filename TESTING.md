# Step365 verification

This document defines reproducible checks. It does not claim that any check passed merely because its procedure or test source exists. The delivery report records the tests actually executed, their results, and the devices/emulators used. Physical-device scenarios below remain pending until someone executes and records them on the intended phone.

## Automated checks

Run from the project root with JDK 21, Android platform 36, and build-tools 36.0.0:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows uses `./gradlew.bat` instead. Inspect `app/build/reports/tests/testDebugUnitTest/index.html`, the XML results under `app/build/test-results/`, and `app/build/reports/lint-results-debug.html`. The CI workflow uploads the APK only after these tasks succeed and preserves available reports even when a check fails.

Domain tests should cover fixed challenge boundaries, exactly 365 inclusive dates, calendar statuses, reliable versus incomplete low totals, streak behavior, personal-goal independence, distance, optional weight, calorie estimates, cumulative baseline, duplicate samples, sensor reset/reboot, and ambiguous date boundaries. Repository/Android smoke tests should verify transactions, persisted reopen, launch, and RemoteViews inflation where supported by the test environment. Read the actual test sources and result reports to establish which cases executed.

Verify the delivery APK independently:

```sh
apksigner verify --verbose --print-certs Step365-debug.apk
aapt dump badging Step365-debug.apk
```

Confirm package `com.step365.app`, app label `Step365`, minimum SDK 26, and target SDK 36. Record a SHA-256 checksum of the exact delivered file. These checks verify packaging and signatures; they do not establish physical walking accuracy.

## Device acceptance checklist

Use the intended phone where possible and an Android 15 device/emulator for compatibility. Keep a separate test device or installation for clock manipulation; changing the system date affects other applications too. Record model, Android/API version, app version, signing fingerprint, and test date with results.

| # | Scenario | Procedure and expected result | Execution status |
|---|---|---|---|
| 1 | First installation and permissions | Install the APK, open it, start tracking, deny activity permission, then grant it through the app/system prompt. Denial produces useful guidance without a crash or invented count. On Android 13+, test notification grant and denial separately. | Physical test pending |
| 2 | Walking steps | With activity permission granted and sensor available, allow the initial baseline, carry the phone and walk a counted route. Confirm subsequent sensor steps increase totals, distance and estimates. Allow for sensor delivery latency; never expect the sensor's pre-install lifetime total to appear as today's count. | Physical test pending |
| 3 | Background collection | Start tracking, return home, lock the screen and walk. Reopen and compare persisted totals and widget. Confirm foreground-service visibility in the notification drawer or Android's active-app surface when notification permission is denied. Repeat under the phone's normal and restricted battery policies. | Physical test pending |
| 4 | Midnight rollover | Record steps before and after a local midnight. Today's display switches to the new date without deleting yesterday. Cross-boundary deltas that cannot be assigned reliably must not be fabricated; the affected data may be incomplete. Verify refresh with no steps too. | Physical test pending |
| 5 | Application restart | Close/reopen the UI during tracking and confirm counts do not reset or duplicate. Kill the process and reopen; verify the stored checkpoint. Force-stop separately: collection can stop and reopening is required. | Physical test pending |
| 6 | Phone reboot | Enable tracking, record steps, reboot, unlock and inspect recovery. Previously stored totals survive. A new cumulative baseline does not add the pre-reboot total. If Android disallows recovery, reopening provides a route to resume, and the interruption is not claimed as complete data. | Physical test pending |
| 7 | Missing step sensor | Run on a sensorless device/emulator or test fixture. The app explains that compatible tracking is unavailable, stays usable for history, and does not create synthetic steps or a useless running service. | Physical test pending |
| 8 | Counter reset | Inject a lower cumulative value through a test fixture, or reproduce a reboot/reset on hardware. Re-baseline safely with no negative steps, inflated lifetime credit, or duplicate historical totals. Use automated reducer tests for deterministic coverage. | Physical test pending |
| 9 | Calendar marking | Check a finished day with at least 10,000 steps, reliable below-goal day, incomplete day, future day, and today. Verify green/red/gray/purple states, click details, monthly completed count, and previous/next navigation. An unfinished current day is never missed. | Physical test pending |
| 10 | Streaks | Use a deterministic fixture with completed, missed and missing days. Check current and longest streaks, including today's incomplete progress, today's completion, first/last challenge dates, and a break after midnight. Completed-day history remains stored. | Physical test pending |
| 11 | Distance | At 10,000 steps and stride 0.70 m, expect 7.00 km. Change stride to 0.80 m and expect 8.00 km from the same persisted steps. Check validation and two-decimal formatting. | Physical test pending |
| 12 | Calories | With no weight, verify an unavailable/add-weight state. At 10,000 steps, stride 0.70 m and weight 70 kg, the formula gives 245 estimated activity kcal. Change weight and check recalculation. Invalid entries must not be saved. | Physical test pending |
| 13 | Home-screen widget | Add, resize, tap, remove and re-add the widget. Confirm tap opens Step365, totals match the database, and step/settings/date changes refresh it. Confirm useful rendering at minimum size and with larger fonts. Do not assume second-by-second or exact 30-minute timing. | Physical test pending |
| 14 | Android 15 compatibility | On API 35, install, launch, grant/deny permissions, navigate all four screens, start/stop the health foreground service, background it, and inspect logs for service/security failures. Check edge-to-edge system-bar padding and notification behavior. Also smoke-test API 26 and target API 36 when available. | Physical test pending |
| 15 | Persistence and device-clock changes | Record activity, stop/restart the app and reopen Room. Confirm history and preferences survive. Change time zone/date on a test device, including moving backward, and confirm no duplicated deltas or overwritten history. Clearing data/uninstall is explicitly destructive and should reset local history. | Physical test pending |
| 16 | Challenge boundaries | Test 21 Nov 2026, 22 Nov 2026, 21 Nov 2027, and 22 Nov 2027 with fixtures/test clock. Verify outside/first/last/ended labels, 365-day duration, fixed 10,000-step scoring, 3,650,000-step target, remaining/time progress and no automatic restart. Personal goal changes do not change challenge achievements. | Physical test pending |

## Result record

Record actual outcomes separately from this checklist: command, exit code, test counts, lint outcome, APK path/hash/signature check, Android launch-test environment, and any limitations. A JVM or Robolectric launch check does not substitute for a physical walking/background/battery test. Sensorless emulators cannot demonstrate real step collection.
