package com.pandora.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF6654D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF261765),
    secondary = Color(0xFF436D50),
    onSecondary = Color.White,
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1D1E1C),
    surface = Color(0xFFFAFAF7),
    onSurface = Color(0xFF1D1E1C),
    surfaceVariant = Color(0xFFF0F0EB),
    onSurfaceVariant = Color(0xFF656760),
    outline = Color(0xFFD8D9D2),
    outlineVariant = Color(0xFFE7E7E1),
    error = Color(0xFFBA3A3A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEB2FF),
    onPrimary = Color(0xFF302076),
    primaryContainer = Color(0xFF453590),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFFA5D5B2),
    onSecondary = Color(0xFF113921),
    background = Color(0xFF111310),
    onBackground = Color(0xFFE5E7E1),
    surface = Color(0xFF111310),
    onSurface = Color(0xFFE5E7E1),
    surfaceVariant = Color(0xFF20231F),
    onSurfaceVariant = Color(0xFFBFC2BA),
    outline = Color(0xFF454941),
    outlineVariant = Color(0xFF2C2F2A),
    error = Color(0xFFFFB4AB),
)

@Composable
fun PandoraTheme(
    preference: AppSettings.ThemePreference,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        AppSettings.ThemePreference.SYSTEM -> isSystemInDarkTheme()
        AppSettings.ThemePreference.LIGHT -> false
        AppSettings.ThemePreference.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
