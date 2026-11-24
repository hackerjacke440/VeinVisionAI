# 0001 Native Android instead of Expo; separate pure-Kotlin core module

## Context
The previous prototype used Expo + expo-camera. The product needs per-frame access to camera buffers, OpenCV
processing, a future LiteRT runtime and possibly USB UVC.

## Decision
Native Kotlin app (Compose, CameraX ImageAnalysis RGBA_8888, OpenCV for Android). No Expo code is kept.
Quality metrics, thresholds, view gating and latency statistics live in a JVM-only `:core` module.

## Reasons
- expo-camera exposes no zero-copy frame stream to native OpenCV; per-frame JS bridging would dominate latency.
- OpenCV, LiteRT and UVC are native libraries; wrapping them for Expo would need custom native modules anyway.
- `:core` can be unit-tested on the JVM without a device. It is not an abstraction layer; it holds the logic that has no Android dependency.

## Consequences
- The Expo prototype's UI code is not reused (it was not available to inspect in this session; see AUDIT.md).
- Only design concepts (dark charcoal / emerald identity, five-tab navigation) carry over.
- Portrait is locked to keep overlay alignment simple; landscape is unsupported.
