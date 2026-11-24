package com.eljihani.veinvision.core

object BenchmarkReport {
    fun format(
        deviceModel: String, androidVersion: String, cameraConfig: String, processingConfig: String,
        warmupFrames: Int, snapshot: LatencyStats.Snapshot, fps: Double?,
    ): String {
        fun f(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.2f ms", it) } ?: "n/a"
        return buildString {
            appendLine("VeinVision AI benchmark (measured on this device, not a validated result)")
            appendLine("Device: $deviceModel")
            appendLine("Android: $androidVersion")
            appendLine("Camera: $cameraConfig")
            appendLine("Processing: $processingConfig")
            appendLine("Warm-up frames discarded: $warmupFrames")
            appendLine("Measured frames in window: ${snapshot.measuredFrames}")
            appendLine("Method: System.nanoTime around per-frame quality + pipeline on the analysis thread; FPS from analyzer arrival timestamps")
            appendLine("Analyzer FPS: ${fps?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "n/a"}")
            appendLine("Latency last: ${f(snapshot.lastMs)}")
            appendLine("Latency mean: ${f(snapshot.meanMs)}")
            appendLine("Latency median: ${f(snapshot.medianMs)}")
            appendLine("Latency p95: ${f(snapshot.p95Ms)}")
        }
    }
}
