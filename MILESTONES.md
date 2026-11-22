# Step365 implementation milestones

Each successful implementation step is verified before its focused commit. No source-only delivery is considered complete.

1. Android project setup and successful baseline APK build (`Setup done`).
2. Pure Kotlin challenge, estimates, counter reducer and unit tests.
3. Transactional Room persistence and preferences.
4. Foreground sensor collection and runtime permission integration.
5. Reboot, clock, date and tracking-gap recovery.
6. Compose dashboard, theme and navigation.
7. Full challenge calendar and day details.
8. Statistics and editable personal settings.
9. Resizable RemoteViews widget and footstep app identity.
10. Automated persistence/launch checks, lint and build corrections.
11. Verified signed debug APK, reproducible CI and installation/testing documentation.

Challenge: 22 November 2026–21 November 2027 inclusive; original goal 10,000 steps; 3,650,000 total steps. Personal goals never change challenge scoring.

## Verified execution

The dependency order put the database-backed widget before its foreground-service caller. The four Compose screens were verified together as one integration milestone. Additional focused commits fixed a replacement-service shutdown race and Android 8 theme compatibility. The first setup commit followed a successful Gradle configuration/wrapper build; subsequent full APK builds and the final clean build succeeded.

Final evidence: 56 passing automated tests; Android 15 JVM launch and widget checks; lint with zero errors; signed APK integrity, metadata and alignment verified. Physical sensor/battery/launcher tests remain explicitly pending. The delivered artifact and detailed evidence are recorded in [VERIFICATION.md](VERIFICATION.md).
