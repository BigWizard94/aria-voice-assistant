package com.bigwizard.aria.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Aria Color Palette ────────────────────────────────────────────────────────

// Primary — Deep violet/indigo (intelligence, trust)
val AriaViolet       = Color(0xFF7C4DFF)
val AriaVioletLight  = Color(0xFFB47CFF)
val AriaVioletDark   = Color(0xFF4A00E0)

// Secondary — Cyan (tech, clarity)
val AriaCyan         = Color(0xFF00E5FF)
val AriaCyanLight    = Color(0xFF80FFFF)
val AriaCyanDark     = Color(0xFF00B2CC)

// Accent — Soft pink (warmth, approachability)
val AriaPink         = Color(0xFFFF4081)

// Neutrals
val AriaDarkBg       = Color(0xFF0A0A1A)   // Near-black with blue tint
val AriaDarkSurface  = Color(0xFF12122A)
val AriaDarkCard     = Color(0xFF1A1A35)
val AriaLightBg      = Color(0xFFF5F5FF)
val AriaLightSurface = Color(0xFFFFFFFF)
val AriaLightCard    = Color(0xFFEEEEFF)

// Status
val AriaSuccess      = Color(0xFF00E676)
val AriaError        = Color(0xFFFF5252)
val AriaWarning      = Color(0xFFFFD740)

// ── Dark Color Scheme ─────────────────────────────────────────────────────────

private val AriaDarkColorScheme = darkColorScheme(
    primary          = AriaViolet,
    onPrimary        = Color.White,
    primaryContainer = AriaVioletDark,
    onPrimaryContainer = AriaVioletLight,

    secondary        = AriaCyan,
    onSecondary      = AriaDarkBg,
    secondaryContainer = AriaCyanDark,
    onSecondaryContainer = AriaCyanLight,

    tertiary         = AriaPink,
    onTertiary       = Color.White,

    background       = AriaDarkBg,
    onBackground     = Color(0xFFE8E8FF),

    surface          = AriaDarkSurface,
    onSurface        = Color(0xFFE8E8FF),
    surfaceVariant   = AriaDarkCard,
    onSurfaceVariant = Color(0xFFB0B0D0),

    error            = AriaError,
    onError          = Color.White,

    outline          = Color(0xFF3A3A5C),
    outlineVariant   = Color(0xFF2A2A45)
)

// ── Light Color Scheme ────────────────────────────────────────────────────────

private val AriaLightColorScheme = lightColorScheme(
    primary          = AriaVioletDark,
    onPrimary        = Color.White,
    primaryContainer = AriaVioletLight,
    onPrimaryContainer = AriaVioletDark,

    secondary        = AriaCyanDark,
    onSecondary      = Color.White,
    secondaryContainer = AriaCyanLight,
    onSecondaryContainer = AriaCyanDark,

    tertiary         = AriaPink,
    onTertiary       = Color.White,

    background       = AriaLightBg,
    onBackground     = Color(0xFF1A1A2E),

    surface          = AriaLightSurface,
    onSurface        = Color(0xFF1A1A2E),
    surfaceVariant   = AriaLightCard,
    onSurfaceVariant = Color(0xFF4A4A6A),

    error            = AriaError,
    onError          = Color.White,

    outline          = Color(0xFFB0B0D0),
    outlineVariant   = Color(0xFFD0D0EE)
)

// ── Theme Composable ──────────────────────────────────────────────────────────

@Composable
fun AriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled — keep Aria's brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> AriaDarkColorScheme
        else      -> AriaLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AriaTypography,
        content     = content
    )
}