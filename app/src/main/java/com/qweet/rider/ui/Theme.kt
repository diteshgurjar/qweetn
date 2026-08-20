package com.qweet.rider.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val QweetPrimary = Color(0xFFA83300)
val QweetPrimaryContainer = Color(0xFFD24200)
val QweetOnPrimary = Color(0xFFFFFFFF)
val QweetSecondary = Color(0xFF006D37) // "online" green from the mockups
val QweetSurface = Color(0xFFF7F9FC)
val QweetSurfaceContainerLowest = Color(0xFFFFFFFF)
val QweetSurfaceContainer = Color(0xFFECEEF1)
val QweetSurfaceContainerHigh = Color(0xFFE6E8EB)
val QweetError = Color(0xFFBA1A1A)
val QweetOnSurface = Color(0xFF191C1E)
val QweetOnSurfaceVariant = Color(0xFF5C4037)
val QweetOutlineVariant = Color(0xFFE5BEB2)

private val QweetColorScheme = lightColorScheme(
    primary = QweetPrimary,
    onPrimary = QweetOnPrimary,
    primaryContainer = QweetPrimaryContainer,
    secondary = QweetSecondary,
    background = QweetSurface,
    surface = QweetSurface,
    surfaceVariant = QweetSurfaceContainerHigh,
    surfaceContainerLowest = QweetSurfaceContainerLowest,
    surfaceContainer = QweetSurfaceContainer,
    surfaceContainerHigh = QweetSurfaceContainerHigh,
    error = QweetError,
    onBackground = QweetOnSurface,
    onSurface = QweetOnSurface,
    onSurfaceVariant = QweetOnSurfaceVariant,
    outlineVariant = QweetOutlineVariant
)

@Composable
fun QweetRiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QweetColorScheme,
        content = content
    )
}
