# VeinVision AI (research prototype)

Native Android (Kotlin, Jetpack Compose, CameraX, OpenCV) vein-pattern visualization research app for the Samsung Galaxy A36 5G.

> VeinVision AI is an experimental research and educational application. It is not a medical device and must not be used to make clinical decisions or guide needle insertion.

**Status: source written, NOT yet built, NOT yet run on any device.** See DEVLOG.md.

## Build (needs Android Studio / Android SDK 35 and network access to Google + Maven Central)
```
# first time only, with a local Gradle: gradle wrapper --gradle-version 8.9
./gradlew :core:test                  # JVM unit tests (quality metrics, thresholds, latency stats)
./gradlew :app:assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest   # OpenCV pipeline tests, needs a device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Library versions in gradle/libs.versions.toml were chosen without resolving them; bump if the sync complains.

## Layout
- `core/` pure Kotlin/JVM: quality metrics, assessor, view gate, latency/FPS, processing config. Unit-testable without Android.
- `app/` Android: CameraX controller, FrameAnalyzer, OpenCV `VesselnessPipeline`, Compose UI (SCAN, SESSIONS, MODELS, HARDWARE, SETTINGS).
- `docs/` AUDIT.md, decisions/, DEVICE_TEST_CHECKLIST.md.
