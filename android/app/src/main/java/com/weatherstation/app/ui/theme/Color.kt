package com.weatherstation.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==============================================================================
// FIGMA WEATHER MOBILE APP UI KIT — COLOR PALETTE & DYNAMIC SKY GRADIENTS
// ==============================================================================

// Deep Atmospheric Backgrounds
val BgDarkStart = Color(0xFF2E335A)
val BgDarkMid = Color(0xFF1C1B33)
val BgDarkEnd = Color(0xFF0F0E17)

val BgLightStart = Color(0xFF1E3A8A)
val BgLightMid = Color(0xFF1E293B)
val BgLightEnd = Color(0xFF0F172A)

// Figma Sheet & Card Backgrounds (Liquid Glassmorphism)
val FigmaGlassBg = Color(0xFF2E335A).copy(alpha = 0.30f)
val FigmaCardBg = Color(0xFF25234A).copy(alpha = 0.55f)
val FigmaCardHighlight = Color(0xFF48319D).copy(alpha = 0.70f)
val FigmaCapsuleActive = Color(0xFF48319D)
val FigmaCapsuleInactive = Color(0xFF2E335A).copy(alpha = 0.40f)

// Glass Borders & Specular Highlights
val FigmaGlassBorder = Color.White.copy(alpha = 0.18f)
val FigmaGlassBorderHighlight = Color(0xFFC427FB).copy(alpha = 0.45f)
val FigmaGrabber = Color.White.copy(alpha = 0.35f)

// Vibrant Accent Colors
val AccentTemp = Color(0xFFFF8A00)       // Vivid Thermal Orange
val AccentHumidity = Color(0xFF40CBD8)   // Neon Aqua Moisture
val AccentLight = Color(0xFFFFD600)      // Solar Gold Sunlight
val AccentPurple = Color(0xFFC427FB)     // Purple Accent
val AccentIndigo = Color(0xFF48319D)     // Royal Indigo
val AccentGreen = Color(0xFF00E676)      // Emerald Status Live

// Legacy / Status Aliases
val StatusGreen = AccentGreen
val StatusOrange = AccentTemp
val StatusRed = Color(0xFFEF4444)
val SkyBluePrimary = AccentIndigo

// Text Hierarchy
val TextWhitePrimary = Color(0xFFFFFFFF)
val TextWhiteSecondary = Color(0xFFEBEBF5).copy(alpha = 0.65f)
val TextWhiteTertiary = Color(0xFFEBEBF5).copy(alpha = 0.40f)

// Porcelain & Glass Sheet Colors
val PorcelainWhite = Color(0xFFFFFFFF)
val PorcelainSheetBg = Color(0xFFFFFFFF)
val PorcelainSheetBorder = Color(0xFFE2E8F0)
val DarkSlateText = Color(0xFF1E2229)
val MutedSlateText = Color(0xFF64748B)

// Frosted Glassmorphism
val AuraGlassCapsuleBg = Color(0xFFFFFFFF).copy(alpha = 0.22f)
val AuraGlassCapsuleBorder = Color(0xFFFFFFFF).copy(alpha = 0.40f)
val AuraGlassDarkBg = Color(0xFF0F172A).copy(alpha = 0.45f)

// Dynamic Atmospheric Background Brushes
fun getDynamicSkyBrush(isDaytime: Boolean, lightPct: Float): Brush {
    return if (isDaytime) {
        if (lightPct > 65f) {
            // Bright Azure Day into Warm Peach Horizon
            Brush.verticalGradient(
                listOf(
                    Color(0xFF74B9FF),
                    Color(0xFFA18CD1),
                    Color(0xFFFFB8B8)
                )
            )
        } else {
            // Golden Sunset / Dusk
            Brush.verticalGradient(
                listOf(
                    Color(0xFF516395),
                    Color(0xFFE87070),
                    Color(0xFFFBC280)
                )
            )
        }
    } else {
        // Deep Midnight Violet with Cosmic Stars
        Brush.verticalGradient(
            listOf(
                Color(0xFF0F172A),
                Color(0xFF1E1B4B),
                Color(0xFF2E1065),
                Color(0xFF3B0764)
            )
        )
    }
}

val FigmaDarkSkyBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2E335A),
        Color(0xFF1C1B33),
        Color(0xFF080720)
    )
)

val FigmaSheetBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF8FAFC)
    )
)

val FigmaCapsuleGlowBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFD000).copy(alpha = 0.6f),
        Color(0xFFFF8A00).copy(alpha = 0.9f)
    )
)

