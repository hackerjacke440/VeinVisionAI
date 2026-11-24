package com.eljihani.veinvision.core

enum class ViewMode(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhanced"),
    VESSELNESS("Vesselness response"),
    MASK("Thresholded mask"),
    OVERLAY("Experimental overlay"),
}

object OverlayGate {
    /** POOR quality forces the original image; every other status honours the user's choice. */
    fun effectiveView(requested: ViewMode, status: QualityStatus?): ViewMode =
        if (status == QualityStatus.POOR) ViewMode.ORIGINAL else requested
}
