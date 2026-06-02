package com.mobisec.omniip.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TacticalDarkColorScheme = darkColorScheme(
    primary = MatrixGreen,
    secondary = TacticalAmber,
    tertiary = AlertRed,
    background = PureBlack,
    surface = DeepCharcoal,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onTertiary = PureBlack,
    onBackground = LightGray,
    onSurface = MatrixGreen
)

@Composable
fun OmniIPTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = TacticalDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
