package com.eljihani.veinvision.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.eljihani.veinvision.ScanViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = VV.Emerald, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(VV.Panel, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
fun SessionsScreen(vm: ScanViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) { vm.sessions.list() }
    Page("SESSIONS") {
        if (files.isEmpty()) Text("No sessions exist. Captured stills appear here (stored on this device only).", color = VV.Muted)
        files.forEach { f -> SessionRow(f, onDelete = { vm.sessions.delete(f); tick++ }) }
    }
}

@Composable
private fun SessionRow(f: File, onDelete: () -> Unit) {
    val bmp = remember(f.path) {
        val o = BitmapFactory.Options().apply { inSampleSize = 8 }
        BitmapFactory.decodeFile(f.path, o)
    }
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(72.dp))
            Column {
                Text(f.name, color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Text("${DateFormat.getDateTimeInstance().format(Date(f.lastModified()))} · ${f.length() / 1024} KB · original camera still (no overlay burned in)", color = VV.Muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
fun ModelsScreen() {
    val ctx = LocalContext.current
    val dir = File(ctx.filesDir, "models")
    val files = dir.listFiles { f -> f.extension.lowercase() in setOf("tflite", "litert") }?.toList() ?: emptyList()
    Page("MODELS") {
        if (files.isEmpty()) {
            Card {
                Text("AI SEGMENTATION MODEL NOT INSTALLED.", color = VV.Warn)
                Text("No model file was found in this app's model folder, and no inference runtime is bundled. All enhancement shown in SCAN is classical image processing, not an AI prediction.", color = VV.Muted)
            }
        } else {
            Card {
                Text("Model file(s) present but NOT loaded:", color = VV.Warn)
                files.forEach { Text("• ${it.name} (${it.length() / 1024} KB)", color = VV.Text) }
                Text("This build contains no LiteRT/TensorFlow Lite runtime and no inference code, so these files are unused. Nothing here is validated.", color = VV.Muted)
            }
        }
    }
}

@Composable
fun HardwareScreen(cameraGranted: Boolean) {
    val ctx = LocalContext.current
    Page("HARDWARE") {
        Card {
            Text("NIR HARDWARE: NOT CONNECTED", color = VV.Warn)
            Text("Detecting a USB device does not show NIR capability. A future NIR path needs verified UVC frames, sensor spectral sensitivity, IR-cut filter status and illumination. This build has no UVC code.", color = VV.Muted)
        }
        Card {
            Text("Device", color = VV.Emerald)
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = VV.Text)
        }
        Text("Built-in cameras (from Camera2 characteristics)", color = VV.Emerald)
        val infos = remember { cameraInfos(ctx) }
        if (infos.isEmpty()) Text("No cameras reported by the system.", color = VV.Muted)
        infos.forEach { Card { Text(it, color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) } }
        Text("Camera permission: ${if (cameraGranted) "granted" else "not granted"}", color = VV.Muted)
        Text("USB devices attached (identity only)", color = VV.Emerald)
        val usb = remember { usbList(ctx) }
        if (usb.isEmpty()) Text("No USB devices attached.", color = VV.Muted)
        usb.forEach { Card { Text(it, color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall); Text("Capability: UNVERIFIED (not treated as a camera or NIR source)", color = VV.Warn, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) } }
    }
}

private fun cameraInfos(ctx: Context): List<String> = try {
    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.cameraIdList.map { id ->
        val c = cm.getCameraCharacteristics(id)
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_BACK -> "back"; CameraCharacteristics.LENS_FACING_FRONT -> "front"; else -> "external/other" }
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) { 0 -> "LIMITED"; 1 -> "FULL"; 2 -> "LEGACY"; 3 -> "LEVEL_3"; 4 -> "EXTERNAL"; else -> "?" }
        val size = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString { String.format(Locale.US, "%.1f", it) }
        val awbLock = c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)
        val maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        "ID $id · $facing · hardware level $level\nSensor ${size?.width}×${size?.height} · focal length(s) mm: $focal\nMax digital zoom $maxZoom · AWB lock available: $awbLock"
    }
} catch (t: Throwable) { listOf("Could not query cameras: ${t.message}") }

private fun usbList(ctx: Context): List<String> = try {
    val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    um.deviceList.values.map { "${it.deviceName} · vendor 0x${Integer.toHexString(it.vendorId)} product 0x${Integer.toHexString(it.productId)} · class ${it.deviceClass}" }
} catch (t: Throwable) { emptyList() }

@Composable
fun SettingsScreen(vm: ScanViewModel) {
    val cfg by vm.config.collectAsState()
    val th by vm.thresholds.collectAsState()
    val clip = LocalClipboardManager.current
    var bench by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    Page("SETTINGS") {
        Card {
            Text("Processing", color = VV.Emerald)
            Labeled("Processing width ${cfg.processingWidth} px (lower = faster)", cfg.processingWidth.toFloat(), 240f..720f) { vm.updateConfig(cfg.copy(processingWidth = (it / 40).toInt() * 40)) }
            OutlinedButton(onClick = { vm.updateConfig(com.eljihani.veinvision.core.ProcessingConfig()) }) { Text("Reset processing defaults") }
        }
        Card {
            Text("Quality thresholds (engineering values, calibrate on device)", color = VV.Emerald)
            Labeled("POOR if mean brightness below ${th.meanBrightnessPoorLow.toInt()}", th.meanBrightnessPoorLow.toFloat(), 10f..100f) { vm.updateThresholds(th.copy(meanBrightnessPoorLow = it.toDouble())) }
            Labeled("POOR if overexposed fraction ≥ ${(th.overFractionPoor * 100).toInt()}%", th.overFractionPoor.toFloat(), 0.05f..0.6f) { vm.updateThresholds(th.copy(overFractionPoor = it.toDouble())) }
            Labeled("POOR if sharpness (VarLap) below ${th.sharpnessPoorBelow.toInt()}", th.sharpnessPoorBelow.toFloat(), 1f..100f) { vm.updateThresholds(th.copy(sharpnessPoorBelow = it.toDouble())) }
            Labeled("POOR if motion MAD above ${th.motionPoorAbove.toInt()}", th.motionPoorAbove.toFloat(), 3f..40f) { vm.updateThresholds(th.copy(motionPoorAbove = it.toDouble())) }
            Labeled("POOR if skin-coloured area below ${(th.skinCoveragePoorBelow * 100).toInt()}%", th.skinCoveragePoorBelow.toFloat(), 0f..0.5f) { vm.updateThresholds(th.copy(skinCoveragePoorBelow = it.toDouble())) }
            OutlinedButton(onClick = { vm.updateThresholds(com.eljihani.veinvision.core.QualityThresholds()) }) { Text("Reset thresholds") }
        }
        Card {
            Text("Benchmark", color = VV.Emerald)
            Text("Run SCAN with a processed view for a while, then generate a report. The first ${com.eljihani.veinvision.processing.FrameAnalyzer.WARMUP_FRAMES} processed frames are discarded.", color = VV.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { bench = vm.benchmarkText() }) { Text("Generate report") }
                OutlinedButton(onClick = { vm.resetBenchmark(); bench = null }) { Text("Reset counters") }
            }
            bench?.let { b -> Text(b, color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.labelSmall); OutlinedButton(onClick = { clip.setText(AnnotatedString(b)) }) { Text("Copy") } }
        }
        Card {
            Text("Storage", color = VV.Emerald)
            val files = remember(tick) { vm.sessions.list() }
            Text("${files.size} capture(s), ${vm.sessions.totalBytes() / 1024} KB\n${vm.sessions.dir.absolutePath}", color = VV.Text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Text("Stored in app-specific storage; removed when the app is uninstalled. Not shared with other apps.", color = VV.Muted)
            OutlinedButton(onClick = { vm.sessions.list().forEach { vm.sessions.delete(it) }; tick++ }) { Text("Delete all captures") }
        }
        Card {
            Text("Privacy", color = VV.Emerald)
            Text("All processing is local. The app declares no INTERNET permission, uploads nothing, and collects no personal information. Settings are stored in app-private preferences.", color = VV.Text)
        }
        Card {
            Text("About", color = VV.Emerald)
            Text("VeinVision AI 0.1.0-research. VeinVision AI is an experimental research and educational application. It is not a medical device and must not be used to make clinical decisions or guide needle insertion.", color = VV.Text)
        }
    }
}
