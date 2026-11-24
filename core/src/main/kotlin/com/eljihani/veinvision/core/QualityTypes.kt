package com.eljihani.veinvision.core

enum class QualityStatus { GOOD, MARGINAL, POOR }

/**
 * Engineering thresholds, NOT clinical thresholds. They are starting values chosen for
 * a first prototype and must be calibrated against real captures on the target device.
 * Each metric has a "marginal" bound (warn) and a "poor" bound (suppress overlay).
 */
data class QualityThresholds(
    val underLumaLevel: Int = 20,          // luma <= this counts as underexposed pixel
    val overLumaLevel: Int = 235,          // luma >= this counts as overexposed pixel
    val meanBrightnessPoorLow: Double = 40.0,
    val meanBrightnessMarginalLow: Double = 70.0,
    val meanBrightnessMarginalHigh: Double = 190.0,
    val meanBrightnessPoorHigh: Double = 220.0,
    val underFractionMarginal: Double = 0.15,
    val underFractionPoor: Double = 0.40,
    val overFractionMarginal: Double = 0.05,
    val overFractionPoor: Double = 0.20,
    val sharpnessMarginalBelow: Double = 60.0,   // variance of Laplacian on the analysis grid
    val sharpnessPoorBelow: Double = 20.0,
    val motionMarginalAbove: Double = 6.0,       // mean abs luma difference between frames (0..255)
    val motionPoorAbove: Double = 15.0,
    val skinCoverageMarginalBelow: Double = 0.30,
    val skinCoveragePoorBelow: Double = 0.10,
)

/** Downscaled analysis frame. luma/cr/cb are 0..255 stored in bytes (unsigned). */
class AnalysisGrid(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val cr: ByteArray,
    val cb: ByteArray,
)

data class QualityMetrics(
    val meanBrightness: Double,
    val brightnessHistogram: IntArray,        // 16 bins over 0..255
    val underFraction: Double,
    val overFraction: Double,
    val sharpnessVarLaplacian: Double,
    /** Frame-to-frame mean abs luma difference. Null on the first frame or after a size change. */
    val motionMad: Double?,
    /** Null when the skin detector result is not considered valid (see SkinDetector). */
    val skinCoverage: Double?,
)

data class QualityReport(
    val status: QualityStatus,
    val reasons: List<String>,
    val metrics: QualityMetrics,
)
