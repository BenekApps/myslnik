package com.benekapps.myslnik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.benekapps.myslnik.data.ThemeMode

// Calm teal palette. Text/background pairs meet WCAG AA (4.5:1) or better.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2B6A6E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEBEE),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4D5F7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E3FF),
    onTertiaryContainer = Color(0xFF071C36),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF181C1C),
    surface = Color(0xFFF7FAF9),
    onSurface = Color(0xFF181C1C),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F4),
    surfaceContainer = Color(0xFFEBEFEE),
    surfaceContainerHigh = Color(0xFFE5E9E8),
    surfaceContainerHighest = Color(0xFFE0E3E3),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD2D6),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF0E5054),
    onPrimaryContainer = Color(0xFFBDEBEE),
    secondary = Color(0xFFB1CCCD),
    onSecondary = Color(0xFF1C3436),
    secondaryContainer = Color(0xFF334B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB5C7EA),
    onTertiary = Color(0xFF1F314C),
    tertiaryContainer = Color(0xFF354764),
    onTertiaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF181C1C),
    surfaceContainer = Color(0xFF1C2020),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535),
    outline = Color(0xFF889393),
    outlineVariant = Color(0xFF3F4949),
)

@Composable
fun MyslnikTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
