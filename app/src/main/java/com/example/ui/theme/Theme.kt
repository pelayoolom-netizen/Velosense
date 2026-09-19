package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VeloColorScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = VeloDarkBg,
    primaryContainer = ElectricLimeGlow,
    onPrimaryContainer = ElectricLime,
    secondary = VeloBlueAccent,
    onSecondary = VeloDarkBg,
    background = VeloDarkBg,
    onBackground = VeloTextPrimary,
    surface = VeloDarkSurface,
    onSurface = VeloTextPrimary,
    surfaceVariant = VeloDarkCard,
    onSurfaceVariant = VeloTextSecondary,
    outline = VeloDarkCardBorder,
    error = VeloError,
    onError = VeloDarkBg
)

@Composable
fun VeloSenseTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VeloDarkBg.toArgb()
            window.navigationBarColor = VeloDarkBg.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = VeloColorScheme,
        typography = VeloTypography,
        content = content
    )
}
