package com.eljihani.veinvision.data

import android.content.Context
import com.eljihani.veinvision.core.DenoiseKind
import com.eljihani.veinvision.core.ProcessingConfig
import com.eljihani.veinvision.core.QualityThresholds

/** Plain SharedPreferences persistence; no accounts, no network. */
class ConfigStore(context: Context) {
    private val p = context.getSharedPreferences("veinvision_config", Context.MODE_PRIVATE)

    fun loadProcessing(): ProcessingConfig {
        val d = ProcessingConfig()
        return ProcessingConfig(
            useGreenChannel = p.getBoolean("green", d.useGreenChannel),
            normalizeEnabled = p.getBoolean("norm", d.normalizeEnabled),
            brightness = p.getFloat("bright", d.brightness.toFloat()).toDouble(),
            contrast = p.getFloat("contrast", d.contrast.toFloat()).toDouble(),
            denoiseEnabled = p.getBoolean("denoise", d.denoiseEnabled),
            denoiseKind = DenoiseKind.valueOf(p.getString("denoiseKind", d.denoiseKind.name)!!),
            noiseReduction = p.getInt("noiseLevel", d.noiseReduction),
            claheEnabled = p.getBoolean("clahe", d.claheEnabled),
            enhancementIntensity = p.getFloat("enh", d.enhancementIntensity.toFloat()).toDouble(),
            vesselnessEnabled = p.getBoolean("vessel", d.vesselnessEnabled),
            detectionThreshold = p.getFloat("thr", d.detectionThreshold.toFloat()).toDouble(),
            overlayOpacity = p.getFloat("opacity", d.overlayOpacity.toFloat()).toDouble(),
            processingWidth = p.getInt("procWidth", d.processingWidth),
        )
    }

    fun saveProcessing(c: ProcessingConfig) = p.edit()
        .putBoolean("green", c.useGreenChannel).putBoolean("norm", c.normalizeEnabled)
        .putFloat("bright", c.brightness.toFloat()).putFloat("contrast", c.contrast.toFloat())
        .putBoolean("denoise", c.denoiseEnabled).putString("denoiseKind", c.denoiseKind.name)
        .putInt("noiseLevel", c.noiseReduction).putBoolean("clahe", c.claheEnabled)
        .putFloat("enh", c.enhancementIntensity.toFloat()).putBoolean("vessel", c.vesselnessEnabled)
        .putFloat("thr", c.detectionThreshold.toFloat()).putFloat("opacity", c.overlayOpacity.toFloat())
        .putInt("procWidth", c.processingWidth).apply()

    fun loadThresholds(): QualityThresholds {
        val d = QualityThresholds()
        return d.copy(
            meanBrightnessPoorLow = p.getFloat("q_darkPoor", d.meanBrightnessPoorLow.toFloat()).toDouble(),
            overFractionPoor = p.getFloat("q_overPoor", d.overFractionPoor.toFloat()).toDouble(),
            sharpnessPoorBelow = p.getFloat("q_sharpPoor", d.sharpnessPoorBelow.toFloat()).toDouble(),
            sharpnessMarginalBelow = p.getFloat("q_sharpMarg", d.sharpnessMarginalBelow.toFloat()).toDouble(),
            motionPoorAbove = p.getFloat("q_motionPoor", d.motionPoorAbove.toFloat()).toDouble(),
            skinCoveragePoorBelow = p.getFloat("q_skinPoor", d.skinCoveragePoorBelow.toFloat()).toDouble(),
        )
    }

    fun saveThresholds(t: QualityThresholds) = p.edit()
        .putFloat("q_darkPoor", t.meanBrightnessPoorLow.toFloat())
        .putFloat("q_overPoor", t.overFractionPoor.toFloat())
        .putFloat("q_sharpPoor", t.sharpnessPoorBelow.toFloat())
        .putFloat("q_sharpMarg", t.sharpnessMarginalBelow.toFloat())
        .putFloat("q_motionPoor", t.motionPoorAbove.toFloat())
        .putFloat("q_skinPoor", t.skinCoveragePoorBelow.toFloat()).apply()
}
