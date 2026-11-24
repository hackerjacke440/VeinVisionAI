# DEVLOG

## 2026-11-24 initial native scaffold
**Build result: NOT BUILT.** The authoring sandbox had no Android SDK and could not reach Maven/Google repositories
(Gradle plugin resolution failed), so nothing was compiled and no tests were run.
**Test results: none executed.** **Device tests: none** (not installed on a Galaxy A36). **Performance: none measured.**

### Written (source only, unverified)
- `:core` quality metrics (mean/histogram, under/over-exposed fraction, variance of Laplacian, frame-to-frame MAD,
  YCrCb skin coverage with validity rule), assessor with reasons, overlay gate (POOR -> original), latency stats (warm-up, mean, median, p95), FPS meter, benchmark report text.
- `:app` CameraX Preview + ImageAnalysis (RGBA, keep-latest) + ImageCapture, zoom, exposure comp., AWB lock (only if reported), lens toggle, camera state/error display.
- OpenCV pipeline: green channel, brightness/contrast, normalize, Gaussian/median denoise, CLAHE, multiscale Hessian vesselness, threshold, overlay; per-stage timings.
- Five screens (SCAN, SESSIONS, MODELS, HARDWARE, SETTINGS), local capture storage, threshold/processing persistence.
- Unit tests (core, JVM) and instrumented pipeline tests (synthetic images only), manual device checklist.

### Known limitations / pending
- Everything above is unverified; expect compile errors on first build (versions in libs.versions.toml unresolved).
- Per-frame allocations remain (new Bitmap per processed frame, some temporary Mats); not profiled or optimised.
- Vesselness c-parameter is frame-adaptive (0.5 * max structure per scale); behaviour on real skin images unknown.
- Skin coverage is a fixed YCrCb colour rule, lighting dependent. Quality thresholds are engineering guesses awaiting calibration.
- Latency counts frames where the pipeline ran only; ORIGINAL view does not run the pipeline.
- Camera-pipeline integration tests (lifecycle, capture, error handling) are manual only (see docs/DEVICE_TEST_CHECKLIST.md).
- Portrait only. Compile/target SDK 35 (Android 15); Android 16 not targeted yet.
- No NIR/UVC code, no AI model or inference runtime (by design).
- Original Expo prototype was not inspected (docs/AUDIT.md).
