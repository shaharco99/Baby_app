package com.oryareach.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette ported verbatim from the web app's design tokens in `src/index.css`, so the two
 * apps look like the same product. Names mirror the CSS custom properties they came from.
 */
internal object Palette {

    object Light {
        val background = Color(0xFFF6EFE1)
        val foreground = Color(0xFF2B2A1F)
        val card = Color(0xFFFFFDF6)
        val cardForeground = Color(0xFF2B2A1F)
        val primary = Color(0xFF6E7F5E)
        val primaryForeground = Color(0xFFF6EFE1)
        val secondary = Color(0xFFE8A268)
        val secondaryForeground = Color(0xFF2B2418)
        val muted = Color(0xFFEAE1CE)
        val mutedForeground = Color(0xFF6B6250)
        val accent = Color(0xFFF0DCD5)
        val accentForeground = Color(0xFF2B2418)
        val moss = Color(0xFF8B9A7B)
        val mossForeground = Color(0xFF24291D)
        val blush = Color(0xFFE0938A)
        val blushForeground = Color(0xFF2B2418)
        val destructive = Color(0xFFB0503A)
        val border = Color(0xFFE0D4BC)
        val ring = Color(0xFF6E7F5E)
    }

    object Dark {
        val background = Color(0xFF2A2636)
        val foreground = Color(0xFFF3ECDD)
        val card = Color(0xFF332E45)
        val cardForeground = Color(0xFFF3ECDD)
        val primary = Color(0xFFE8A268)
        val primaryForeground = Color(0xFF2A2636)
        val secondary = Color(0xFF8FA080)
        val secondaryForeground = Color(0xFF1F1D2B)
        val muted = Color(0xFF3A3450)
        val mutedForeground = Color(0xFFC7BFAE)
        val accent = Color(0xFF453C4F)
        val accentForeground = Color(0xFFF3ECDD)
        val moss = Color(0xFF8FA080)
        val mossForeground = Color(0xFF1F1D2B)
        val blush = Color(0xFFE7A9A0)
        val blushForeground = Color(0xFF2A2636)
        val destructive = Color(0xFFE07856)
        val border = Color(0xFF46405C)
        val ring = Color(0xFFE8A268)
    }
}

/**
 * The moon countdown paints its own night sky in both themes, exactly as the web version
 * does, so the hero does not flip to a light background in day mode.
 */
object NightPalette {
    val sky = Color(0xFF292540)
    val moonDim = Color(0xFF332E4A)
    val moonMid = Color(0xFF463F5E)
    val moonRim = Color(0xFF544C6E)
    val glowStart = Color(0xFFFBD8AC)
    val glowMid = Color(0xFFE8A268)
    val glowEnd = Color(0xFFC9793F)
    val text = Color(0xFFF3ECDD)
    val textMuted = Color(0xFFC9C2AE)

    /** Split-world glitch easter egg (long-press the moon) — two contrasting "story world"
     * tones that flicker across [sky] before settling back. */
    val glitchWorldOne = Color(0xFF1B3A5C)
    val glitchWorldTwo = Color(0xFF5C1B4A)
}
