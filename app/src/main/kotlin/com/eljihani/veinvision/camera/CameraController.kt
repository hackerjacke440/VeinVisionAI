package com.eljihani.veinvision.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** What the bound camera actually reports. Anything unsupported is false/null and the UI hides or disables it. */
data class CameraCaps(
    val lensFacing: Int,
    val hasBack: Boolean,
    val hasFront: Boolean,
    val minZoom: Float,
    val maxZoom: Float,
    val evSupported: Boolean,
    val evMin: Int,
    val evMax: Int,
    val evStepStops: Float,
    val awbLockSupported: Boolean,
)

sealed interface CameraStatus {
    data object Idle : CameraStatus
    data object Opening : CameraStatus
    data object Open : CameraStatus
    data class Error(val message: String) : CameraStatus
}

@OptIn(ExperimentalCamera2Interop::class)
class CameraController(private val context: Context) {
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var stateObserver: androidx.lifecycle.Observer<CameraState>? = null
    private var observedOwner: LifecycleOwner? = null

    var onStatus: (CameraStatus) -> Unit = {}
    var onCaps: (CameraCaps?) -> Unit = {}

    fun bind(owner: LifecycleOwner, previewView: PreviewView, analyzer: ImageAnalysis.Analyzer, lensFacing: Int) {
        onStatus(CameraStatus.Opening)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val facing = if (lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront) CameraSelector.LENS_FACING_FRONT
                else if (hasBack) CameraSelector.LENS_FACING_BACK else if (hasFront) CameraSelector.LENS_FACING_FRONT
                else { onStatus(CameraStatus.Error("No camera available on this device")); onCaps(null); return@addListener }
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()

                val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().also { it.setAnalyzer(analysisExecutor, analyzer) }
                val capture = ImageCapture.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = capture

                p.unbindAll()
                clearObserver()
                val cam = p.bindToLifecycle(owner, selector, preview, analysis, capture)
                camera = cam

                val obs = androidx.lifecycle.Observer<CameraState> { st ->
                    val err = st.error
                    when {
                        err != null -> onStatus(CameraStatus.Error("Camera error (code ${err.code}) – camera may be disconnected or in use by another app"))
                        st.type == CameraState.Type.OPEN -> onStatus(CameraStatus.Open)
                        st.type == CameraState.Type.CLOSED -> onStatus(CameraStatus.Idle)
                        else -> onStatus(CameraStatus.Opening)
                    }
                }
                stateObserver = obs; observedOwner = owner
                cam.cameraInfo.cameraState.observe(owner, obs)

                val zoom = cam.cameraInfo.zoomState.value
                val ev = cam.cameraInfo.exposureState
                val awbAvail = try {
                    Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
                } catch (_: Throwable) { false }
                onCaps(CameraCaps(
                    lensFacing = facing, hasBack = hasBack, hasFront = hasFront,
                    minZoom = zoom?.minZoomRatio ?: 1f, maxZoom = zoom?.maxZoomRatio ?: 1f,
                    evSupported = ev.isExposureCompensationSupported,
                    evMin = ev.exposureCompensationRange.lower, evMax = ev.exposureCompensationRange.upper,
                    evStepStops = ev.exposureCompensationStep.toFloat(),
                    awbLockSupported = awbAvail,
                ))
            } catch (t: Throwable) {
                onStatus(CameraStatus.Error("Camera failed to start: ${t.message}")); onCaps(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun clearObserver() {
        val o = stateObserver; val ow = observedOwner
        if (o != null && ow != null) camera?.cameraInfo?.cameraState?.removeObserver(o)
        stateObserver = null; observedOwner = null
    }

    fun setZoomRatio(r: Float) { camera?.cameraControl?.setZoomRatio(r) }
    fun setExposureIndex(i: Int) { camera?.cameraControl?.setExposureCompensationIndex(i) }

    fun setAwbLock(locked: Boolean) {
        val cam = camera ?: return
        try {
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(
                CaptureRequestOptions.Builder().setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked).build())
        } catch (_: Throwable) { /* reported unavailable via CameraCaps */ }
    }

    fun capture(file: File, onDone: (Result<File>) -> Unit) {
        val c = imageCapture ?: return onDone(Result.failure(IllegalStateException("Camera not ready")))
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        c.takePicture(opts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) = onDone(Result.success(file))
            override fun onError(e: ImageCaptureException) = onDone(Result.failure(e))
        })
    }

    fun unbind() { clearObserver(); provider?.unbindAll(); camera = null }
    fun shutdown() { unbind(); analysisExecutor.shutdown() }
}
