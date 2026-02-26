package com.familymovies.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFFE040FB)
val Pink = Color(0xFFFF6B9D)
val DarkBackground = Color(0xFF0D0D1A)
val DarkSurface = Color(0xFF1A1A2E)
val DarkCard = Color(0xFF2D1B4E)

private val AppColorScheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0080),
    onPrimaryContainer = Color(0xFFFFD6FF),
    secondary = Pink,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFCCC2DC),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

@Composable
fun FamilyMoviesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
