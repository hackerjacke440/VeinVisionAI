package com.eljihani.veinvision.processing

import com.eljihani.veinvision.core.DenoiseKind
import com.eljihani.veinvision.core.ProcessingConfig
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/** Per-stage wall-clock timings in nanoseconds; 0 when a stage was skipped. */
data class StageTimings(
    val grayscaleNs: Long = 0, val normalizeNs: Long = 0, val denoiseNs: Long = 0,
    val claheNs: Long = 0, val vesselnessNs: Long = 0, val thresholdNs: Long = 0, val overlayNs: Long = 0,
)

/**
 * All Mats are RGBA (CV_8UC4) for display so they can be converted straight to Bitmaps.
 * Mats are owned by the pipeline and overwritten on the next process() call; consume them
 * (e.g. copy to a Bitmap) before calling process() again from the same thread.
 */
class PipelineResult(
    val original: Mat,
    val enhanced: Mat,
    val vesselness: Mat?,
    val mask: Mat?,
    val overlay: Mat?,
    val timings: StageTimings,
)

/**
 * Classical, configurable pipeline: green channel -> brightness/contrast -> normalize ->
 * denoise -> CLAHE -> multiscale Hessian vesselness (Frangi-style, dark ridges) -> threshold -> overlay.
 * Output is "Vesselness Response" / "Experimental Overlay": it highlights ridge-like dark structures,
 * which may or may not be veins. It is never an AI prediction.
 *
 * Not thread-safe: use from a single analysis thread.
 */
class VesselnessPipeline {
    private val src = Mat()
    private val gray = Mat()
    private val work = Mat()
    private val enhancedGray = Mat()
    private val enhancedRgba = Mat()
    private val vessel32 = Mat()
    private val input32 = Mat()
    private val vesselGray = Mat()
    private val vesselRgba = Mat()
    private val mask8 = Mat()
    private val maskRgba = Mat()
    private val overlayRgba = Mat()
    private val tint = Mat()
    private var clahe: CLAHE? = null
    private var claheClip = -1.0

    // Hessian scratch
    private val g = Mat(); private val a = Mat(); private val b = Mat(); private val c = Mat()
    private val t1 = Mat(); private val t2 = Mat(); private val e1 = Mat(); private val e2 = Mat()
    private val rb = Mat(); private val s2 = Mat(); private val resp = Mat(); private val tmp = Mat()

    fun process(rgbaIn: Mat, cfg: ProcessingConfig): PipelineResult {
        var tGray = 0L; var tNorm = 0L; var tDen = 0L; var tClahe = 0L; var tVes = 0L; var tThr = 0L; var tOv = 0L

        // Downscale to processing width (keeps aspect ratio).
        if (rgbaIn.cols() > cfg.processingWidth) {
            val scale = cfg.processingWidth.toDouble() / rgbaIn.cols()
            Imgproc.resize(rgbaIn, src, Size(cfg.processingWidth.toDouble(), rgbaIn.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        } else {
            rgbaIn.copyTo(src)
        }

        var t0 = System.nanoTime()
        if (cfg.useGreenChannel) Core.extractChannel(src, gray, 1) else Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        // Brightness/contrast always act on the grayscale working image (alpha*x + beta).
        gray.convertTo(work, CvType.CV_8U, cfg.contrast, cfg.brightness)
        tGray = System.nanoTime() - t0

        if (cfg.normalizeEnabled) {
            t0 = System.nanoTime()
            Core.normalize(work, work, 0.0, 255.0, Core.NORM_MINMAX)
            tNorm = System.nanoTime() - t0
        }
        if (cfg.denoiseEnabled) {
            t0 = System.nanoTime()
            val k = 2 * cfg.noiseReduction.coerceIn(1, 5) + 1
            when (cfg.denoiseKind) {
                DenoiseKind.GAUSSIAN -> Imgproc.GaussianBlur(work, work, Size(k.toDouble(), k.toDouble()), 0.0)
                DenoiseKind.MEDIAN -> Imgproc.medianBlur(work, work, k)
            }
            tDen = System.nanoTime() - t0
        }
        if (cfg.claheEnabled) {
            t0 = System.nanoTime()
            if (clahe == null || claheClip != cfg.enhancementIntensity) {
                clahe = Imgproc.createCLAHE(cfg.enhancementIntensity, Size(8.0, 8.0))
                claheClip = cfg.enhancementIntensity
            }
            clahe!!.apply(work, enhancedGray)
            tClahe = System.nanoTime() - t0
        } else {
            work.copyTo(enhancedGray)
        }
        Imgproc.cvtColor(enhancedGray, enhancedRgba, Imgproc.COLOR_GRAY2RGBA)

        var vesselOut: Mat? = null
        var maskOut: Mat? = null
        var overlayOut: Mat? = null

        if (cfg.vesselnessEnabled) {
            t0 = System.nanoTime()
            computeVesselness(enhancedGray, cfg)
            vessel32.convertTo(vesselGray, CvType.CV_8U, 255.0)
            Imgproc.cvtColor(vesselGray, vesselRgba, Imgproc.COLOR_GRAY2RGBA)
            vesselOut = vesselRgba
            tVes = System.nanoTime() - t0

            t0 = System.nanoTime()
            Imgproc.threshold(vesselGray, mask8, cfg.detectionThreshold * 255.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.cvtColor(mask8, maskRgba, Imgproc.COLOR_GRAY2RGBA)
            maskOut = maskRgba
            tThr = System.nanoTime() - t0

            t0 = System.nanoTime()
            // Emerald tint blended over the ORIGINAL colour frame only where the mask is set.
            src.copyTo(overlayRgba)
            tint.create(src.size(), CvType.CV_8UC4)
            tint.setTo(Scalar(16.0, 185.0, 129.0, 255.0)) // emerald (R,G,B,A)
            Core.addWeighted(src, 1.0 - cfg.overlayOpacity, tint, cfg.overlayOpacity, 0.0, tmp)
            tmp.copyTo(overlayRgba, mask8)
            overlayOut = overlayRgba
            tOv = System.nanoTime() - t0
        }

        return PipelineResult(src, enhancedRgba, vesselOut, maskOut, overlayOut, StageTimings(tGray, tNorm, tDen, tClahe, tVes, tThr, tOv))
    }

    /**
     * Multiscale Hessian ridge measure for DARK ridges on a bright background.
     * Per scale s: G = gaussian(I, s); Hessian via Sobel second derivatives scaled by s^2.
     * Eigenvalues e1>=e2 (e1 large positive for a dark line). V = exp(-Rb^2/(2*beta^2)) * (1-exp(-S^2/(2*c^2))),
     * Rb=|e2|/e1, S^2=e1^2+e2^2, c = 0.5*max(S) of the scale (frame-adaptive). Result is max over scales, in 0..1,
     * written to [vessel32].
     */
    private fun computeVesselness(grayU8: Mat, cfg: ProcessingConfig) {
        grayU8.convertTo(input32, CvType.CV_32F, 1.0 / 255.0)
        resp.create(input32.size(), CvType.CV_32F)
        resp.setTo(Scalar(0.0))
        val steps = max(1, cfg.sigmaSteps)
        val twoBeta2 = 2.0 * cfg.frangiBeta * cfg.frangiBeta
        for (i in 0 until steps) {
            val sigma = if (steps == 1) cfg.sigmaMin else cfg.sigmaMin + (cfg.sigmaMax - cfg.sigmaMin) * i / (steps - 1)
            val s2n = sigma * sigma
            Imgproc.GaussianBlur(input32, g, Size(0.0, 0.0), sigma)
            Imgproc.Sobel(g, a, CvType.CV_32F, 2, 0, 3); Core.multiply(a, Scalar(s2n), a)
            Imgproc.Sobel(g, c, CvType.CV_32F, 0, 2, 3); Core.multiply(c, Scalar(s2n), c)
            Imgproc.Sobel(g, b, CvType.CV_32F, 1, 1, 3); Core.multiply(b, Scalar(s2n), b)
            // eigenvalues = (a+c)/2 +/- sqrt(((a-c)/2)^2 + b^2)
            Core.add(a, c, t1); Core.multiply(t1, Scalar(0.5), t1)
            Core.subtract(a, c, t2); Core.multiply(t2, Scalar(0.5), t2)
            Core.magnitude(t2, b, tmp)
            Core.add(t1, tmp, e1)
            Core.subtract(t1, tmp, e2)
            // Rb^2 term
            Core.max(e1, Scalar(1e-6), rb)
            Core.absdiff(e2, Scalar(0.0), tmp)
            Core.divide(tmp, rb, rb)
            Core.multiply(rb, rb, rb)
            Core.multiply(rb, Scalar(-1.0 / twoBeta2), rb)
            Core.exp(rb, rb)
            // structureness term
            Core.multiply(e1, e1, s2)
            Core.multiply(e2, e2, tmp)
            Core.add(s2, tmp, s2)
            val maxS = Math.sqrt(Core.minMaxLoc(s2).maxVal)
            val cc = 0.5 * maxS + 1e-9
            Core.multiply(s2, Scalar(-1.0 / (2.0 * cc * cc)), s2)
            Core.exp(s2, s2)
            Core.multiply(s2, Scalar(-1.0), s2)
            Core.add(s2, Scalar(1.0), s2) // 1 - exp(...)
            Core.multiply(rb, s2, rb)
            // dark ridge only: e1 > 0
            Imgproc.threshold(e1, tmp, 0.0, 1.0, Imgproc.THRESH_BINARY)
            Core.multiply(rb, tmp, rb)
            Core.max(resp, rb, resp)
        }
        resp.copyTo(vessel32)
    }

    fun release() {
        listOf(src, gray, work, enhancedGray, enhancedRgba, vessel32, input32, vesselGray, vesselRgba, mask8, maskRgba,
            overlayRgba, tint, g, a, b, c, t1, t2, e1, e2, rb, s2, resp, tmp).forEach { it.release() }
    }
}
