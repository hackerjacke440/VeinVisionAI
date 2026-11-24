package com.eljihani.veinvision.core

object QualityAssessor {
    fun assess(m: QualityMetrics, t: QualityThresholds = QualityThresholds()): QualityReport {
        val poor = mutableListOf<String>()
        val marginal = mutableListOf<String>()

        fun fmt(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

        when {
            m.meanBrightness < t.meanBrightnessPoorLow -> poor += "Too dark (mean ${fmt(m.meanBrightness)})"
            m.meanBrightness > t.meanBrightnessPoorHigh -> poor += "Too bright (mean ${fmt(m.meanBrightness)})"
            m.meanBrightness < t.meanBrightnessMarginalLow -> marginal += "Dim (mean ${fmt(m.meanBrightness)})"
            m.meanBrightness > t.meanBrightnessMarginalHigh -> marginal += "Bright (mean ${fmt(m.meanBrightness)})"
        }
        when {
            m.underFraction >= t.underFractionPoor -> poor += "Underexposed pixels ${fmt(m.underFraction * 100)}%"
            m.underFraction >= t.underFractionMarginal -> marginal += "Underexposed pixels ${fmt(m.underFraction * 100)}%"
        }
        when {
            m.overFraction >= t.overFractionPoor -> poor += "Overexposed pixels ${fmt(m.overFraction * 100)}%"
            m.overFraction >= t.overFractionMarginal -> marginal += "Overexposed pixels ${fmt(m.overFraction * 100)}%"
        }
        when {
            m.sharpnessVarLaplacian < t.sharpnessPoorBelow -> poor += "Low sharpness indicator (VarLap ${fmt(m.sharpnessVarLaplacian)})"
            m.sharpnessVarLaplacian < t.sharpnessMarginalBelow -> marginal += "Reduced sharpness indicator (VarLap ${fmt(m.sharpnessVarLaplacian)})"
        }
        m.motionMad?.let { mad ->
            when {
                mad > t.motionPoorAbove -> poor += "High frame-to-frame motion (MAD ${fmt(mad)})"
                mad > t.motionMarginalAbove -> marginal += "Frame-to-frame motion (MAD ${fmt(mad)})"
            }
        }
        // Skin coverage only contributes when the detector produced a valid result.
        m.skinCoverage?.let { c ->
            when {
                c < t.skinCoveragePoorBelow -> poor += "Little skin-coloured area (${fmt(c * 100)}%)"
                c < t.skinCoverageMarginalBelow -> marginal += "Limited skin-coloured area (${fmt(c * 100)}%)"
            }
        }

        val status = when {
            poor.isNotEmpty() -> QualityStatus.POOR
            marginal.isNotEmpty() -> QualityStatus.MARGINAL
            else -> QualityStatus.GOOD
        }
        val reasons = when (status) {
            QualityStatus.POOR -> poor + marginal
            QualityStatus.MARGINAL -> marginal
            QualityStatus.GOOD -> listOf("All measured indicators within configured thresholds")
        }
        return QualityReport(status, reasons, m)
    }
}
