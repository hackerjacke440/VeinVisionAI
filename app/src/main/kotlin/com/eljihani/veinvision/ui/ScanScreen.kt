package com.eljihani.veinvision.ui

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eljihani.veinvision.ScanViewModel
import com.eljihani.veinvision.camera.CameraStatus
import com.eljihani.veinvision.core.DenoiseKind
import com.eljihani.veinvision.core.QualityStatus
import com.eljihani.veinvision.core.ViewMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cfg by vm.config.collectAsState()
    val view by vm.view.collectAsState()
    val frame by vm.frame.collectAsState()
    val status by vm.status.collectAsState()
    val caps by vm.caps.collectAsState()
    val lens by vm.lens.collectAsState()
    val message by vm.message.collectAsState()
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var awbLocked by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var ev by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(false) }

    // Re-bind only when the lens changes or the screen (re)enters; switching view modes never rebinds.
    DisposableEffect(owner, lens) {
        vm.camera.bind(owner, previewView, vm.analyzer, lens)
        onDispose { vm.camera.unbind() }
    }
    LaunchedEffect(caps) { zoom = 1f; ev = 0f; awbLocked = false }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("VISIBLE-LIGHT EXPERIMENTAL MODE", color = VV.Warn, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        Text("NIR HARDWARE: NOT CONNECTED  ·  AI MODEL: NOT INSTALLED", color = VV.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)

        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(VV.Black, RoundedCornerShape(8.dp))) {
            AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
            val bmp = frame?.processed
            if (bmp != null && frame?.effectiveView != ViewMode.ORIGINAL) {
                Image(bmp.asImageBitmap(), contentDescription = frame?.effectiveView?.label, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
            }
            Text(
                (frame?.effectiveView ?: ViewMode.ORIGINAL).label.uppercase(Locale.US),
                color = VV.Emerald, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(VV.Black.copy(alpha = 0.6f)).padding(4.dp),
            )
        }

        when (val s = status) {
            is CameraStatus.Error -> Text(s.message, color = VV.Bad)
            CameraStatus.Opening -> Text("Opening camera…", color = VV.Muted)
            else -> {}
        }
        frame?.error?.let { Text(it, color = VV.Bad) }

        val q = frame?.quality
        if (q != null) {
            val col = when (q.status) { QualityStatus.GOOD -> VV.Emerald; QualityStatus.MARGINAL -> VV.Warn; QualityStatus.POOR -> VV.Bad }
            Column(Modifier.fillMaxWidth().background(VV.Panel, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("IMAGE QUALITY: ${q.status}", color = col, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                q.reasons.forEach { Text("• $it", color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                val m = q.metrics
                Text(
                    "mean ${f1(m.meanBrightness)} · under ${f1(m.underFraction * 100)}% · over ${f1(m.overFraction * 100)}% · VarLap ${f1(m.sharpnessVarLaplacian)} · " +
                        "motion(MAD) ${m.motionMad?.let { f1(it) } ?: "n/a"} · skin ${m.skinCoverage?.let { f1(it * 100) + "%" } ?: "n/a (invalid)"}",
                    color = VV.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
            if (q.status == QualityStatus.POOR && view != ViewMode.ORIGINAL) {
                Text("Image quality POOR: experimental overlay suppressed. Showing original camera image.", color = VV.Bad)
            }
        }

        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ViewMode.values().forEach { vmode ->
                FilterChip(selected = view == vmode, onClick = { vm.setView(vmode) }, label = { Text(vmode.label) })
            }
        }

        frame?.let { f ->
            val l = f.latency
            Text(
                "Camera ${f.cameraWidth}×${f.cameraHeight} · Processing ${f.processingWidth}×${f.processingHeight} · FPS ${f.fps?.let { f1(it) } ?: "n/a"}\n" +
                    (if (l.warmingUp) "Latency: warming up (first ${com.eljihani.veinvision.processing.FrameAnalyzer.WARMUP_FRAMES} processed frames discarded)"
                    else "Latency last ${f1(l.lastMs)} · mean ${l.meanMs?.let { f1(it) }} · median ${l.medianMs?.let { f1(it) }} · p95 ${l.p95Ms?.let { f1(it) }} ms (n=${l.measuredFrames})") +
                    "\nLatency is counted only while a processed view is selected.",
                color = VV.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.capture() }) { Text("Capture still") }
            OutlinedButton(onClick = { showControls = !showControls }) { Text(if (showControls) "Hide controls" else "Controls") }
            caps?.let { c -> if (c.hasBack && c.hasFront) OutlinedButton(onClick = { vm.toggleLens() }) { Text(if (lens == CameraSelector.LENS_FACING_BACK) "Use front" else "Use back") } }
        }
        message?.let { Text(it, color = VV.Emerald); LaunchedEffect(it) { kotlinx.coroutines.delay(3000); vm.clearMessage() } }

        if (showControls) {
            Column(Modifier.fillMaxWidth().background(VV.Panel, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("Camera", color = VV.Emerald)
                val c = caps
                if (c != null && c.maxZoom > c.minZoom) {
                    Labeled("Zoom ${f1(zoom.toDouble())}×", zoom, c.minZoom..c.maxZoom) { zoom = it; vm.camera.setZoomRatio(it) }
                } else Text("Zoom: unavailable on this camera", color = VV.Muted)
                if (c != null && c.evSupported && c.evMax > c.evMin) {
                    Labeled("Exposure comp. index ${ev.toInt()}", ev, c.evMin.toFloat()..c.evMax.toFloat()) { ev = it; vm.camera.setExposureIndex(it.toInt()) }
                } else Text("Exposure compensation: unavailable", color = VV.Muted)
                if (c?.awbLockSupported == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(awbLocked, { awbLocked = it; vm.camera.setAwbLock(it) }); Text("  White-balance lock", color = VV.Text)
                    }
                } else Text("White-balance lock: unavailable", color = VV.Muted)

                Text("Processing stages", color = VV.Emerald, modifier = Modifier.padding(top = 8.dp))
                Toggle("Green channel (off = luma)", cfg.useGreenChannel) { vm.updateConfig(cfg.copy(useGreenChannel = it)) }
                Toggle("Normalize", cfg.normalizeEnabled) { vm.updateConfig(cfg.copy(normalizeEnabled = it)) }
                Toggle("Denoise (${cfg.denoiseKind})", cfg.denoiseEnabled) { vm.updateConfig(cfg.copy(denoiseEnabled = it)) }
                OutlinedButton(onClick = { vm.updateConfig(cfg.copy(denoiseKind = if (cfg.denoiseKind == DenoiseKind.GAUSSIAN) DenoiseKind.MEDIAN else DenoiseKind.GAUSSIAN)) }) { Text("Switch to ${if (cfg.denoiseKind == DenoiseKind.GAUSSIAN) "median" else "Gaussian"}") }
                Toggle("CLAHE", cfg.claheEnabled) { vm.updateConfig(cfg.copy(claheEnabled = it)) }
                Toggle("Vesselness + threshold + overlay", cfg.vesselnessEnabled) { vm.updateConfig(cfg.copy(vesselnessEnabled = it)) }

                Labeled("Brightness ${cfg.brightness.toInt()}", cfg.brightness.toFloat(), -100f..100f) { vm.updateConfig(cfg.copy(brightness = it.toDouble())) }
                Labeled("Contrast ${f1(cfg.contrast)}", cfg.contrast.toFloat(), 0.5f..2f) { vm.updateConfig(cfg.copy(contrast = it.toDouble())) }
                Labeled("Enhancement intensity (CLAHE clip) ${f1(cfg.enhancementIntensity)}", cfg.enhancementIntensity.toFloat(), 1f..8f) { vm.updateConfig(cfg.copy(enhancementIntensity = it.toDouble())) }
                Labeled("Noise reduction level ${cfg.noiseReduction}", cfg.noiseReduction.toFloat(), 1f..5f) { vm.updateConfig(cfg.copy(noiseReduction = it.toInt())) }
                Labeled("Detection threshold ${"%.2f".format(Locale.US, cfg.detectionThreshold)}", cfg.detectionThreshold.toFloat(), 0.02f..0.8f) { vm.updateConfig(cfg.copy(detectionThreshold = it.toDouble())) }
                Labeled("Overlay opacity ${"%.2f".format(Locale.US, cfg.overlayOpacity)}", cfg.overlayOpacity.toFloat(), 0f..1f) { vm.updateConfig(cfg.copy(overlayOpacity = it.toDouble())) }
            }
        }
        Text("VeinVision AI is an experimental research and educational application. It is not a medical device and must not be used to make clinical decisions or guide needle insertion.",
            color = VV.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(value, onChange); Text("  $label", color = VV.Text) }
}

@Composable
fun Labeled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Text(label, color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall); Slider(value, onChange, valueRange = range) }
}
