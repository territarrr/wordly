package com.wordly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Clean minimal" palette: warm off-white surfaces with a single emerald accent (amber is only
 * used for favorites). Dynamic wallpaper colors are deliberately not used — they made the app
 * look different on every phone and washed out the accent.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F2E6),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4F6158),
    secondaryContainer = Color(0xFFE6ECE8),
    onSecondaryContainer = Color(0xFF1E2A24),
    tertiary = Color(0xFFC07A12),
    tertiaryContainer = Color(0xFFFFE9C7),
    onTertiaryContainer = Color(0xFF4A2C00),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFAF8F4),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFEDEBE6),
    onSurfaceVariant = Color(0xFF5F625D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2EE),
    surfaceContainer = Color(0xFFEFEDE8),
    surfaceContainerHigh = Color(0xFFE9E7E2),
    surfaceContainerHighest = Color(0xFFE3E1DC),
    outline = Color(0xFFBDBBB5),
    outlineVariant = Color(0xFFDEDCD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FDBB5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF0B5A45),
    onPrimaryContainer = Color(0xFFD3F2E6),
    secondary = Color(0xFFB3C9BE),
    secondaryContainer = Color(0xFF34413B),
    onSecondaryContainer = Color(0xFFDDE7E1),
    tertiary = Color(0xFFF2B95C),
    tertiaryContainer = Color(0xFF5C3D00),
    onTertiaryContainer = Color(0xFFFFE9C7),
    background = Color(0xFF121413),
    onBackground = Color(0xFFE3E3DF),
    surface = Color(0xFF121413),
    onSurface = Color(0xFFE3E3DF),
    surfaceVariant = Color(0xFF2A2D2B),
    onSurfaceVariant = Color(0xFFA9ADA8),
    surfaceContainerLowest = Color(0xFF0D0F0E),
    surfaceContainerLow = Color(0xFF1A1C1B),
    surfaceContainer = Color(0xFF1E211F),
    surfaceContainerHigh = Color(0xFF282B29),
    surfaceContainerHighest = Color(0xFF333634),
    outline = Color(0xFF5C605C),
    outlineVariant = Color(0xFF3A3D3B),
)

private val WordlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val WordlyTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun WordlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WordlyTypography,
        shapes = WordlyShapes,
        content = content,
    )
}
