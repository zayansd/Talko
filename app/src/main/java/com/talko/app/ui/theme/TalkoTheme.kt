package com.talko.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Brand colours ─────────────────────────────────────────────────────────────
val TalkoPrimary       = Color(0xFF4C4FDE)
val TalkoPrimaryDark   = Color(0xFF3A3DC8)
val TalkoSecondary     = Color(0xFF6C63FF)
val TalkoBackground    = Color(0xFFF7F7FF)
val TalkoSurface       = Color(0xFFFFFFFF)
val TalkoOnPrimary     = Color(0xFFFFFFFF)
val TalkoError         = Color(0xFFD32F2F)
val TalkoGreen         = Color(0xFF00C853)
val TalkoGrey          = Color(0xFF8B8C9B)
val TalkoGreyLight     = Color(0xFFF0F0F8)
val TalkoBubbleMine    = Color(0xFF5C6BC0)
val TalkoBubbleOther   = Color(0xFFFFFFFF)
val TalkoTextPrimary   = Color(0xFF1A1A2E)
val TalkoTextSecondary = Color(0xFF5A5B6E)

private val LightColors = lightColorScheme(
    primary          = TalkoPrimary,
    onPrimary        = TalkoOnPrimary,
    secondary        = TalkoSecondary,
    background       = TalkoBackground,
    surface          = TalkoSurface,
    error            = TalkoError,
    onBackground     = TalkoTextPrimary,
    onSurface        = TalkoTextPrimary,
    surfaceVariant   = TalkoGreyLight,
    onSurfaceVariant = TalkoTextSecondary,
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF7B7EFF),
    onPrimary        = Color(0xFF1A1A2E),
    secondary        = TalkoSecondary,
    background       = Color(0xFF0F0F1A),
    surface          = Color(0xFF1A1A2E),
    error            = TalkoError,
    onBackground     = Color(0xFFE8E8FF),
    onSurface        = Color(0xFFE8E8FF),
    surfaceVariant   = Color(0xFF252538),
    onSurfaceVariant = Color(0xFFAAAAAF),
)

// ── App-level dark mode state (driven by Settings DataStore) ──────────────────
val LocalDarkMode = compositionLocalOf { false }

@Composable
fun TalkoTheme(
    forceDark: Boolean? = null,          // null = follow system
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = forceDark ?: systemDark

    CompositionLocalProvider(LocalDarkMode provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColors else LightColors,
            content = content,
        )
    }
}
