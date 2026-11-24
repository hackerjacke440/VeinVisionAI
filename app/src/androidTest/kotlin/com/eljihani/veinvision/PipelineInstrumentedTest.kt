package com.eljihani.veinvision

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eljihani.veinvision.core.ProcessingConfig
import com.eljihani.veinvision.processing.VesselnessPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Run on a device/emulator: ./gradlew :app:connectedDebugAndroidTest
 * SYNTHETIC images only: these verify that the algorithm responds to a dark line and that controls
 * change the output. They are not evidence that real veins are detected.
 */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {
    private lateinit var pipeline: VesselnessPipeline

    @Before fun setUp() {
        assertTrue("OpenCV must load", OpenCVLoader.initLocal())
        pipeline = VesselnessPipeline()
    }

    private fun lineImage(): Mat {
        val m = Mat(240, 320, CvType.CV_8UC4, Scalar(200.0, 200.0, 200.0, 255.0))
        Imgproc.line(m, Point(20.0, 120.0), Point(300.0, 120.0), Scalar(60.0, 60.0, 60.0, 255.0), 5)
        return m
    }

    @Test fun vesselnessIsHigherOnDarkLineThanBackground() {
        val r = pipeline.process(lineImage(), ProcessingConfig(processingWidth = 320))
        val v = r.vesselness!!
        val g = Mat(); Core.extractChannel(v, g, 0)
        val onLine = g.get(120, 160)[0]
        val off = g.get(30, 160)[0]
        assertTrue("line=$onLine off=$off", onLine > off + 40)
    }

    @Test fun thresholdChangesMask() {
        val img = lineImage()
        val lo = pipeline.process(img, ProcessingConfig(detectionThreshold = 0.05, processingWidth = 320)).mask!!
        val loCount = Core.countNonZero(Mat().also { Core.extractChannel(lo, it, 0) })
        val hi = pipeline.process(img, ProcessingConfig(detectionThreshold = 0.9, processingWidth = 320)).mask!!
        val hiCount = Core.countNonZero(Mat().also { Core.extractChannel(hi, it, 0) })
        assertTrue("lo=$loCount hi=$hiCount", loCount > hiCount)
    }

    @Test fun brightnessAndContrastChangeEnhancedImage() {
        val img = lineImage()
        val base = ProcessingConfig(normalizeEnabled = false, claheEnabled = false, denoiseEnabled = false, vesselnessEnabled = false, processingWidth = 320)
        val a = Core.mean(pipeline.process(img, base).enhanced).`val`[0]
        val b = Core.mean(pipeline.process(img, base.copy(brightness = -50.0)).enhanced).`val`[0]
        assertTrue("a=$a b=$b", b < a - 20)
    }

    @Test fun disabledStagesAreSkipped() {
        val r = pipeline.process(lineImage(), ProcessingConfig(vesselnessEnabled = false, claheEnabled = false, denoiseEnabled = false, processingWidth = 320))
        assertNull(r.vesselness); assertNull(r.mask); assertNull(r.overlay)
        assertEquals(0L, r.timings.claheNs); assertEquals(0L, r.timings.denoiseNs); assertEquals(0L, r.timings.vesselnessNs)
    }

    @Test fun processingWidthDownscales() {
        val big = Mat(480, 640, CvType.CV_8UC4, Scalar(128.0, 128.0, 128.0, 255.0))
        val r = pipeline.process(big, ProcessingConfig(processingWidth = 320))
        assertEquals(320, r.enhanced.cols()); assertEquals(240, r.enhanced.rows())
        assertNotNull(r.overlay)
    }

    @Test fun overlayOpacityChangesOverlayOnMaskedPixels() {
        val img = lineImage()
        val c = ProcessingConfig(detectionThreshold = 0.1, processingWidth = 320)
        val lowOp = pipeline.process(img, c.copy(overlayOpacity = 0.0)).overlay!!.clone()
        val hiOp = pipeline.process(img, c.copy(overlayOpacity = 1.0)).overlay!!.clone()
        val diff = Mat(); Core.absdiff(lowOp, hiOp, diff)
        assertTrue(Core.sumElems(diff).`val`[0] > 0.0)
    }
}
