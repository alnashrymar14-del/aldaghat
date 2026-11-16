package com.abbas.aldaghat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F2E4),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFFB26A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF111C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111C19),
    surfaceVariant = Color(0xFFE3ECE8),
    onSurfaceVariant = Color(0xFF41514C),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFF71807B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FDCC3),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFB9F2E4),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF442A00),
    secondaryContainer = Color(0xFF623F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0D1512),
    onBackground = Color(0xFFDFE4E1),
    surface = Color(0xFF141D1A),
    onSurface = Color(0xFFDFE4E1),
    surfaceVariant = Color(0xFF2A3632),
    onSurfaceVariant = Color(0xFFBFCAC5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF89948F),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun AlDaghatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content,
    )
}
