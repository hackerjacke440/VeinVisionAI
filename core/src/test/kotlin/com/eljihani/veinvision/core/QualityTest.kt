package com.eljihani.veinvision.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic frames test algorithm behaviour only; they are not evidence of vein detection. */
class QualityTest {
    private val t = QualityThresholds()

    private fun solidGrid(w: Int, h: Int, y: Int, cr: Int = 128, cb: Int = 128) =
        AnalysisGrid(w, h, ByteArray(w * h) { y.toByte() }, ByteArray(w * h) { cr.toByte() }, ByteArray(w * h) { cb.toByte() })

    private fun checkerGrid(w: Int, h: Int, lo: Int, hi: Int): AnalysisGrid {
        val l = ByteArray(w * h) { i -> (if (((i % w) + (i / w)) % 2 == 0) lo else hi).toByte() }
        return AnalysisGrid(w, h, l, ByteArray(w * h) { 150.toByte() }, ByteArray(w * h) { 100.toByte() })
    }

    @Test fun meanAndExposureFractions() {
        val m = QualityMetricsCalculator.compute(solidGrid(32, 32, 10), null, t)
        assertEquals(10.0, m.meanBrightness, 1e-9)
        assertEquals(1.0, m.underFraction, 1e-9)
        assertEquals(0.0, m.overFraction, 1e-9)
        val w = QualityMetricsCalculator.compute(solidGrid(32, 32, 250), null, t)
        assertEquals(1.0, w.overFraction, 1e-9)
    }

    @Test fun histogramBinsSumToPixelCount() {
        val m = QualityMetricsCalculator.compute(checkerGrid(16, 16, 0, 255), null, t)
        assertEquals(256, m.brightnessHistogram.sum())
        assertEquals(128, m.brightnessHistogram[0])
        assertEquals(128, m.brightnessHistogram[15])
    }

    @Test fun flatImageHasZeroLaplacianVariance_checkerHasHigh() {
        assertEquals(0.0, QualityMetricsCalculator.varianceOfLaplacian(solidGrid(32, 32, 128)), 1e-9)
        assertTrue(QualityMetricsCalculator.varianceOfLaplacian(checkerGrid(32, 32, 50, 200)) > 1000.0)
    }

    @Test fun motionIsNullWithoutComparablePreviousFrame() {
        val a = solidGrid(16, 16, 100)
        assertNull(QualityMetricsCalculator.meanAbsDiff(a, null))
        assertNull(QualityMetricsCalculator.meanAbsDiff(a, solidGrid(8, 8, 100)))
        assertEquals(30.0, QualityMetricsCalculator.meanAbsDiff(solidGrid(16, 16, 130), a)!!, 1e-9)
    }

    @Test fun skinCoverageValidOnlyWhenLumaInRange() {
        assertEquals(1.0, SkinDetector.coverage(solidGrid(16, 16, 128, cr = 150, cb = 100))!!, 1e-9)
        assertEquals(0.0, SkinDetector.coverage(solidGrid(16, 16, 128, cr = 128, cb = 128))!!, 1e-9)
        assertNull(SkinDetector.coverage(solidGrid(16, 16, 10, cr = 150, cb = 100)))
        assertNull(SkinDetector.coverage(solidGrid(4, 4, 128, cr = 150, cb = 100)))
    }

    @Test fun gridFromRgbaConvertsAndSamples() {
        val w = 8; val h = 8
        val rgba = ByteArray(w * h * 4)
        for (i in 0 until w * h) { rgba[i * 4] = 255.toByte(); rgba[i * 4 + 1] = 255.toByte(); rgba[i * 4 + 2] = 255.toByte(); rgba[i * 4 + 3] = 255.toByte() }
        val g = QualityMetricsCalculator.gridFromRgba(rgba, w, h, w * 4, targetWidth = 4)
        assertEquals(4, g.width); assertEquals(4, g.height)
        assertEquals(255, g.luma[0].toInt() and 0xFF)
    }

    @Test fun poorWhenDark_withReason() {
        val r = QualityAssessor.assess(QualityMetricsCalculator.compute(solidGrid(32, 32, 5), null, t), t)
        assertEquals(QualityStatus.POOR, r.status)
        assertTrue(r.reasons.any { it.startsWith("Too dark") })
    }

    @Test fun goodForWellExposedSharpSkinToneFrame() {
        val m = QualityMetrics(120.0, IntArray(16), 0.0, 0.0, 500.0, 1.0, 0.6)
        val r = QualityAssessor.assess(m, t)
        assertEquals(QualityStatus.GOOD, r.status)
        assertTrue(r.reasons.isNotEmpty())
    }

    @Test fun marginalOnModerateMotion_poorOnHighMotion() {
        val base = QualityMetrics(120.0, IntArray(16), 0.0, 0.0, 500.0, 8.0, 0.6)
        assertEquals(QualityStatus.MARGINAL, QualityAssessor.assess(base, t).status)
        assertEquals(QualityStatus.POOR, QualityAssessor.assess(base.copy(motionMad = 20.0), t).status)
    }

    @Test fun invalidSkinResultDoesNotAffectStatus() {
        val m = QualityMetrics(120.0, IntArray(16), 0.0, 0.0, 500.0, null, null)
        assertEquals(QualityStatus.GOOD, QualityAssessor.assess(m, t).status)
    }

    @Test fun thresholdsAreCalibratable() {
        val m = QualityMetrics(120.0, IntArray(16), 0.0, 0.0, 40.0, null, null)
        assertEquals(QualityStatus.MARGINAL, QualityAssessor.assess(m, t).status)
        assertEquals(QualityStatus.GOOD, QualityAssessor.assess(m, t.copy(sharpnessMarginalBelow = 30.0)).status)
    }

    @Test fun poorQualityForcesOriginalView() {
        for (v in ViewMode.values()) assertEquals(ViewMode.ORIGINAL, OverlayGate.effectiveView(v, QualityStatus.POOR))
        assertEquals(ViewMode.OVERLAY, OverlayGate.effectiveView(ViewMode.OVERLAY, QualityStatus.MARGINAL))
        assertEquals(ViewMode.MASK, OverlayGate.effectiveView(ViewMode.MASK, QualityStatus.GOOD))
        assertEquals(ViewMode.VESSELNESS, OverlayGate.effectiveView(ViewMode.VESSELNESS, null))
    }
}
