package com.eljihani.veinvision

import android.app.Application
import org.opencv.android.OpenCVLoader

class VeinVisionApp : Application() {
    /** True only if the OpenCV native library actually loaded. The UI shows an error otherwise. */
    var openCvReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        openCvReady = try { OpenCVLoader.initLocal() } catch (_: Throwable) { false }
    }
}
