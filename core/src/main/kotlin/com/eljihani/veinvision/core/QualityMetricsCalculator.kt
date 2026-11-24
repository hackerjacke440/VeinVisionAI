package com.eljihani.veinvision.core

object QualityMetricsCalculator {

    /**
     * Builds an analysis grid by nearest-neighbour sampling an RGBA_8888 buffer.
     * Sampling (not averaging) keeps this cheap on the analysis thread; sharpness values
     * are therefore only comparable at the same grid width (default 160).
     */
    fun gridFromRgba(
        rgba: ByteArray, width: Int, height: Int, rowStrideBytes: Int, targetWidth: Int = 160,
    ): AnalysisGrid {
        require(width > 0 && height > 0 && targetWidth > 0)
        val gw = minOf(targetWidth, width)
        val gh = maxOf(1, (height.toLong() * gw / width).toInt())
        val luma = ByteArray(gw * gh)
        val cr = ByteArray(gw * gh)
        val cb = ByteArray(gw * gh)
        for (y in 0 until gh) {
            val sy = (y.toLong() * height / gh).toInt()
            for (x in 0 until gw) {
                val sx = (x.toLong() * width / gw).toInt()
                val o = sy * rowStrideBytes + sx * 4
                val r = rgba[o].toInt() and 0xFF
                val g = rgba[o + 1].toInt() and 0xFF
                val b = rgba[o + 2].toInt() and 0xFF
                // BT.601 full-range
                val yv = 0.299 * r + 0.587 * g + 0.114 * b
                val crv = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
                val cbv = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
                val i = y * gw + x
                luma[i] = Math.round(yv).toInt().coerceIn(0, 255).toByte()
                cr[i] = Math.round(crv).toInt().coerceIn(0, 255).toByte()
                cb[i] = Math.round(cbv).toInt().coerceIn(0, 255).toByte()
            }
        }
        return AnalysisGrid(gw, gh, luma, cr, cb)
    }

    fun compute(grid: AnalysisGrid, previous: AnalysisGrid?, t: QualityThresholds): QualityMetrics {
        val n = grid.width * grid.height
        val hist = IntArray(16)
        var sum = 0L
        var under = 0
        var over = 0
        for (i in 0 until n) {
            val v = grid.luma[i].toInt() and 0xFF
            sum += v
            hist[v shr 4]++
            if (v <= t.underLumaLevel) under++
            if (v >= t.overLumaLevel) over++
        }
        return QualityMetrics(
            meanBrightness = sum.toDouble() / n,
            brightnessHistogram = hist,
            underFraction = under.toDouble() / n,
            overFraction = over.toDouble() / n,
            sharpnessVarLaplacian = varianceOfLaplacian(grid),
            motionMad = meanAbsDiff(grid, previous),
            skinCoverage = SkinDetector.coverage(grid),
        )
    }

    /** Variance of the 4-neighbour Laplacian over interior pixels. Single-frame blur indicator only. */
    fun varianceOfLaplacian(g: AnalysisGrid): Double {
        if (g.width < 3 || g.height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until g.height - 1) {
            for (x in 1 until g.width - 1) {
                val i = y * g.width + x
                val l = 4 * (g.luma[i].toInt() and 0xFF) -
                    (g.luma[i - 1].toInt() and 0xFF) - (g.luma[i + 1].toInt() and 0xFF) -
                    (g.luma[i - g.width].toInt() and 0xFF) - (g.luma[i + g.width].toInt() and 0xFF)
                sum += l
                sumSq += l.toDouble() * l
                count++
            }
        }
        val mean = sum / count
        return sumSq / count - mean * mean
    }

    /** Measured frame-to-frame change (camera/subject motion proxy). Null if no comparable previous frame. */
    fun meanAbsDiff(cur: AnalysisGrid, prev: AnalysisGrid?): Double? {
        if (prev == null || prev.width != cur.width || prev.height != cur.height) return null
        val n = cur.width * cur.height
        var s = 0L
        for (i in 0 until n) {
            s += kotlin.math.abs((cur.luma[i].toInt() and 0xFF) - (prev.luma[i].toInt() and 0xFF))
        }
        return s.toDouble() / n
    }
}

/**
 * Skin-region detector: fixed YCrCb box rule (Cr 133..173, Cb 77..127; Chai & Ngan style).
 * It is a crude colour rule, not a learned segmenter, and is lighting dependent.
 * Result is declared invalid (null) when mean luma is outside 30..225 or when the
 * grid is smaller than 8x8 pixels, because the rule is meaningless on near-black/white frames.
 */
object SkinDetector {
    fun coverage(g: AnalysisGrid): Double? {
        val n = g.width * g.height
        if (g.width < 8 || g.height < 8) return null
        var lumaSum = 0L
        var skin = 0
        for (i in 0 until n) {
            lumaSum += g.luma[i].toInt() and 0xFF
            val cr = g.cr[i].toInt() and 0xFF
            val cb = g.cb[i].toInt() and 0xFF
            if (cr in 133..173 && cb in 77..127) skin++
        }
        val mean = lumaSum.toDouble() / n
        if (mean < 30.0 || mean > 225.0) return null
        return skin.toDouble() / n
    }
}
