package com.eljihani.veinvision

import android.app.Application
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import com.eljihani.veinvision.camera.CameraCaps
import com.eljihani.veinvision.camera.CameraController
import com.eljihani.veinvision.camera.CameraStatus
import com.eljihani.veinvision.core.BenchmarkReport
import com.eljihani.veinvision.core.ProcessingConfig
import com.eljihani.veinvision.core.QualityThresholds
import com.eljihani.veinvision.core.ViewMode
import com.eljihani.veinvision.data.ConfigStore
import com.eljihani.veinvision.data.SessionStore
import com.eljihani.veinvision.processing.FrameAnalyzer
import com.eljihani.veinvision.processing.FrameOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    val configStore = ConfigStore(app)
    val sessions = SessionStore(app)
    val camera = CameraController(app)

    private val _config = MutableStateFlow(configStore.loadProcessing())
    val config: StateFlow<ProcessingConfig> = _config.asStateFlow()
    private val _thresholds = MutableStateFlow(configStore.loadThresholds())
    val thresholds: StateFlow<QualityThresholds> = _thresholds.asStateFlow()
    private val _view = MutableStateFlow(ViewMode.ORIGINAL)
    val view: StateFlow<ViewMode> = _view.asStateFlow()
    private val _frame = MutableStateFlow<FrameOutput?>(null)
    val frame: StateFlow<FrameOutput?> = _frame.asStateFlow()
    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Idle)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()
    private val _caps = MutableStateFlow<CameraCaps?>(null)
    val caps: StateFlow<CameraCaps?> = _caps.asStateFlow()
    private val _lens = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lens: StateFlow<Int> = _lens.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val analyzer = FrameAnalyzer { _frame.value = it }.also {
        it.config = _config.value; it.thresholds = _thresholds.value
    }

    init {
        camera.onStatus = { _status.value = it }
        camera.onCaps = { _caps.value = it }
    }

    fun updateConfig(c: ProcessingConfig) { _config.value = c; analyzer.config = c; configStore.saveProcessing(c) }
    fun updateThresholds(t: QualityThresholds) { _thresholds.value = t; analyzer.thresholds = t; configStore.saveThresholds(t) }
    fun setView(v: ViewMode) { _view.value = v; analyzer.requestedView = v }
    fun toggleLens() {
        _lens.value = if (_lens.value == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    }
    fun resetBenchmark() { analyzer.latency.reset() }
    fun clearMessage() { _message.value = null }

    fun capture() {
        camera.capture(sessions.newFile()) { r ->
            _message.value = r.fold({ "Saved ${it.name} (local storage only)" }, { "Capture failed: ${it.message}" })
        }
    }

    fun benchmarkText(): String {
        val f = _frame.value
        return BenchmarkReport.format(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cameraConfig = "lens=${if (_lens.value == CameraSelector.LENS_FACING_BACK) "back" else "front"}, analysis ${f?.cameraWidth}x${f?.cameraHeight} RGBA_8888, KEEP_ONLY_LATEST",
            processingConfig = "${_config.value.summary()} view=${_view.value} (latency counted only for frames where the pipeline ran)",
            warmupFrames = FrameAnalyzer.WARMUP_FRAMES,
            snapshot = analyzer.latency.snapshot(),
            fps = analyzer.fps.fps(),
        )
    }

    override fun onCleared() { camera.shutdown(); analyzer.release() }
}
