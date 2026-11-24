package com.eljihani.veinvision.processing

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.eljihani.veinvision.core.AnalysisGrid
import com.eljihani.veinvision.core.FpsMeter
import com.eljihani.veinvision.core.LatencyStats
import com.eljihani.veinvision.core.OverlayGate
import com.eljihani.veinvision.core.ProcessingConfig
import com.eljihani.veinvision.core.QualityAssessor
import com.eljihani.veinvision.core.QualityMetricsCalculator
import com.eljihani.veinvision.core.QualityReport
import com.eljihani.veinvision.core.QualityStatus
import com.eljihani.veinvision.core.QualityThresholds
import com.eljihani.veinvision.core.ViewMode
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

data class FrameOutput(
    /** Null when the frame failed before quality could be measured (see [error]). */
    val quality: QualityReport?,
    val requestedView: ViewMode,
    val effectiveView: ViewMode,
    /** Non-null only when effectiveView != ORIGINAL and processing succeeded. */
    val processed: Bitmap?,
    val cameraWidth: Int,
    val cameraHeight: Int,
    val processingWidth: Int,
    val processingHeight: Int,
    val latency: LatencyStats.Snapshot,
    val fps: Double?,
    val stageTimings: StageTimings?,
    val error: String?,
)

/**
 * Runs on a single background executor (never the UI thread). With KEEP_ONLY_LATEST backpressure,
 * frames that arrive while we are busy are dropped by CameraX rather than queued.
 * Live preview of ORIGINAL is rendered by PreviewView, not by this analyzer.
 */
class FrameAnalyzer(private val sink: (FrameOutput) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var config: ProcessingConfig = ProcessingConfig()
    @Volatile var thresholds: QualityThresholds = QualityThresholds()
    @Volatile var requestedView: ViewMode = ViewMode.ORIGINAL

    val latency = LatencyStats(windowSize = 300, warmupFrames = WARMUP_FRAMES)
    val fps = FpsMeter()
    private val pipeline = VesselnessPipeline()
    private var previousGrid: AnalysisGrid? = null

    private var raw: Mat? = null
    private val scaled = Mat()
    private val rotated = Mat()
    private val small = Mat()
    private var smallBytes = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        val t0 = System.nanoTime()
        fps.tick(t0)
        try {
            val plane = image.planes[0]
            val buf: ByteBuffer = plane.buffer
            val w = image.width
            val h = image.height
            // Zero-copy wrap of the camera buffer; stride handled by Mat step.
            val m = Mat(h, w, CvType.CV_8UC4, buf, plane.rowStride.toLong())
            raw?.release(); raw = m

            val cfg = config
            val rot = image.imageInfo.rotationDegrees
            val swap = rot == 90 || rot == 270
            // Pre-shrink so that the ROTATED width equals cfg.processingWidth.
            val longSide = if (swap) h else w
            val scale = minOf(1.0, cfg.processingWidth.toDouble() / longSide)
            if (scale < 1.0) Imgproc.resize(m, scaled, Size(w * scale, h * scale), 0.0, 0.0, Imgproc.INTER_AREA) else m.copyTo(scaled)
            when (rot) {
                90 -> org.opencv.core.Core.rotate(scaled, rotated, org.opencv.core.Core.ROTATE_90_CLOCKWISE)
                180 -> org.opencv.core.Core.rotate(scaled, rotated, org.opencv.core.Core.ROTATE_180)
                270 -> org.opencv.core.Core.rotate(scaled, rotated, org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> scaled.copyTo(rotated)
            }

            // Quality: 160-wide grid from an area-averaged small copy.
            Imgproc.resize(rotated, small, Size(160.0, rotated.rows() * 160.0 / rotated.cols()), 0.0, 0.0, Imgproc.INTER_AREA)
            val need = small.rows() * small.cols() * 4
            if (smallBytes.size != need) smallBytes = ByteArray(need)
            small.get(0, 0, smallBytes)
            val grid = QualityMetricsCalculator.gridFromRgba(smallBytes, small.cols(), small.rows(), small.cols() * 4, 160)
            val metrics = QualityMetricsCalculator.compute(grid, previousGrid, thresholds)
            previousGrid = grid
            val report = QualityAssessor.assess(metrics, thresholds)

            val requested = requestedView
            val effective = OverlayGate.effectiveView(requested, report.status)
            var bitmap: Bitmap? = null
            var timings: StageTimings? = null
            if (effective != ViewMode.ORIGINAL) {
                val res = pipeline.process(rotated, cfg)
                timings = res.timings
                val out: Mat? = when (effective) {
                    ViewMode.ENHANCED -> res.enhanced
                    ViewMode.VESSELNESS -> res.vesselness
                    ViewMode.MASK -> res.mask
                    ViewMode.OVERLAY -> res.overlay
                    ViewMode.ORIGINAL -> null
                }
                if (out != null) {
                    bitmap = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(out, bitmap)
                }
                latency.add((System.nanoTime() - t0) / 1e6)
            }
            sink(FrameOutput(report, requested, effective, bitmap, w, h, rotated.cols(), rotated.rows(),
                latency.snapshot(), fps.fps(), timings, null))
        } catch (t: Throwable) {
            // Processing failure must not crash the app or the camera pipeline.
            sink(FrameOutput(
                null,
                requestedView, ViewMode.ORIGINAL, null, image.width, image.height, 0, 0,
                latency.snapshot(), fps.fps(), null, "Processing error: ${t.javaClass.simpleName}: ${t.message}"))
        } finally {
            image.close() // always release the buffer back to CameraX
        }
    }

    fun release() { raw?.release(); pipeline.release(); scaled.release(); rotated.release(); small.release() }

    companion object { const val WARMUP_FRAMES = 30 }
}
