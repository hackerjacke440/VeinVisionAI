package com.eljihani.veinvision.core

enum class DenoiseKind { GAUSSIAN, MEDIAN }

/** Every field is consumed by the OpenCV pipeline; there are no decorative parameters. */
data class ProcessingConfig(
    val useGreenChannel: Boolean = true,      // false => luma conversion instead of green extraction
    val normalizeEnabled: Boolean = true,     // min-max stretch to 0..255
    val brightness: Double = 0.0,             // additive, -100..100 (applied before normalize)
    val contrast: Double = 1.0,               // multiplicative, 0.5..2.0
    val denoiseEnabled: Boolean = true,
    val denoiseKind: DenoiseKind = DenoiseKind.GAUSSIAN,
    val noiseReduction: Int = 1,              // 1..5 => kernel size 2n+1
    val claheEnabled: Boolean = true,
    val enhancementIntensity: Double = 2.0,   // CLAHE clip limit, 1..8
    val vesselnessEnabled: Boolean = true,
    val sigmaMin: Double = 1.5,
    val sigmaMax: Double = 4.0,
    val sigmaSteps: Int = 3,
    val frangiBeta: Double = 0.5,
    val detectionThreshold: Double = 0.15,    // on vesselness 0..1
    val overlayOpacity: Double = 0.6,         // 0..1
    val processingWidth: Int = 480,           // analysis is downscaled to this width
) {
    fun summary(): String =
        "green=$useGreenChannel norm=$normalizeEnabled b=$brightness c=$contrast " +
            "denoise=${if (denoiseEnabled) "$denoiseKind/$noiseReduction" else "off"} " +
            "clahe=${if (claheEnabled) enhancementIntensity else "off"} " +
            "vesselness=${if (vesselnessEnabled) "σ$sigmaMin-$sigmaMax×$sigmaSteps β$frangiBeta" else "off"} " +
            "thr=$detectionThreshold opacity=$overlayOpacity width=$processingWidth"
}
