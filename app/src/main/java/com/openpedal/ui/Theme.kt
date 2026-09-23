package com.openpedal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7F397),
    onPrimary = Color(0xFF17320D),
    secondary = Color(0xFFB8D0A9),
    background = Color(0xFF101410),
    surface = Color(0xFF171D16),
    surfaceVariant = Color(0xFF293128),
    onSurfaceVariant = Color(0xFFC4CEC0),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF326A1C),
    onPrimary = Color.White,
    secondary = Color(0xFF4D6545),
    background = Color(0xFFF8FBF3),
    surface = Color(0xFFF0F5EB),
    surfaceVariant = Color(0xFFE0E9D9),
    onSurfaceVariant = Color(0xFF444B40),
)

@Composable
fun OpenPedalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
