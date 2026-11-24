package com.eljihani.veinvision.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VV {
    val Black = Color(0xFF0A0C0B)
    val Charcoal = Color(0xFF151918)
    val Panel = Color(0xFF1D2321)
    val Emerald = Color(0xFF10B981)
    val Text = Color(0xFFE6EDEA)
    val Muted = Color(0xFF93A19B)
    val Warn = Color(0xFFF59E0B)
    val Bad = Color(0xFFEF4444)
}

@Composable
fun VeinVisionTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = VV.Emerald, onPrimary = VV.Black, background = VV.Black, onBackground = VV.Text,
        surface = VV.Charcoal, onSurface = VV.Text, surfaceVariant = VV.Panel, onSurfaceVariant = VV.Muted,
        secondaryContainer = VV.Panel, onSecondaryContainer = VV.Emerald,
    ),
    content = content,
)
