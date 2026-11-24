package com.eljihani.veinvision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eljihani.veinvision.ui.HardwareScreen
import com.eljihani.veinvision.ui.ModelsScreen
import com.eljihani.veinvision.ui.ScanScreen
import com.eljihani.veinvision.ui.SessionsScreen
import com.eljihani.veinvision.ui.SettingsScreen
import com.eljihani.veinvision.ui.VV
import com.eljihani.veinvision.ui.VeinVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cvReady = (application as VeinVisionApp).openCvReady
        setContent {
            VeinVisionTheme {
                if (!cvReady) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("OpenCV native library failed to load. Image processing is unavailable; the app cannot run the pipeline.", color = VV.Bad)
                    }
                    return@VeinVisionTheme
                }
                val vm: ScanViewModel = viewModel()
                var granted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf("SCAN", "SESSIONS", "MODELS", "HARDWARE", "SETTINGS")
                Scaffold(
                    containerColor = VV.Black,
                    bottomBar = {
                        NavigationBar(containerColor = VV.Charcoal) {
                            tabs.forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = { Text(label.take(1), color = if (tab == i) VV.Emerald else VV.Muted) },
                                    label = { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = VV.Panel, selectedTextColor = VV.Emerald, unselectedTextColor = VV.Muted),
                                )
                            }
                        }
                    },
                ) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        if (tab == 0 && !granted) {
                            Column(Modifier.padding(24.dp)) {
                                Text("Camera permission is needed for live preview and capture. Images are processed on this device only.", color = VV.Text)
                                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) { Text("Grant camera permission") }
                            }
                        } else when (tab) {
                            0 -> ScanScreen(vm)
                            1 -> SessionsScreen(vm)
                            2 -> ModelsScreen()
                            3 -> HardwareScreen(granted)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
