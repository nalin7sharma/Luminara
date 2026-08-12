package com.luminara.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Luminara's palette. Deep navy ground, violet for the product, teal for
// "understood / verified", amber for evidence taken from the board.
val Ink = Color(0xFF0B1020)
val InkElevated = Color(0xFF141A2E)
val InkCard = Color(0xFF1A2138)
val InkBorder = Color(0xFF2A3350)
val Violet = Color(0xFF7C5CFF)
val VioletSoft = Color(0xFF9E88FF)
val Teal = Color(0xFF38E1C6)
val Amber = Color(0xFFFFB454)
val Rose = Color(0xFFFF6B81)
val TextPrimary = Color(0xFFEDF0F7)
val TextSecondary = Color(0xFF9AA3B8)
val TextFaint = Color(0xFF6B7391)

private val LuminaraDark = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = VioletSoft,
    secondary = Teal,
    onSecondary = Color(0xFF042B24),
    secondaryContainer = Color(0xFF10352F),
    onSecondaryContainer = Teal,
    tertiary = Amber,
    background = Ink,
    onBackground = TextPrimary,
    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextSecondary,
    outline = InkBorder,
    outlineVariant = Color(0xFF232B45),
    error = Rose,
    onError = Color.White,
)

private val LuminaraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Luminara is a dark-first product; the scheme is intentionally fixed so the
 *  lecture material always renders against the same calibrated contrast. */
@Composable
fun LuminaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LuminaraDark,
        typography = LuminaraTypography,
        content = content,
    )
}
