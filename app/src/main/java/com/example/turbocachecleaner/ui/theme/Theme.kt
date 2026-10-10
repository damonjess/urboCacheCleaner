package com.example.turbocachecleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Brand palette (dark) ----
val TurboBackground = Color(0xFF070B16)
val TurboSurface = Color(0xFF111726)
val TurboSurfaceVariant = Color(0xFF1B2436)
val TurboOutline = Color(0xFF2B3652)
val TurboCyan = Color(0xFF2DD4E8)
val TurboCyanDark = Color(0xFF0891A6)
val TurboViolet = Color(0xFF8B5CF6)
val TurboVioletDark = Color(0xFF6D28D9)
val TurboGreen = Color(0xFF34D399)
val TurboAmber = Color(0xFFFBBF24)
val TurboRed = Color(0xFFF43F5E)
val TurboTextPrimary = Color(0xFFEAF0FF)
val TurboTextSecondary = Color(0xFF97A3C2)

// ---- Brand palette (light) ----
val LightBackground = Color(0xFFF4F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7ECF6)
val LightOutline = Color(0xFFCBD5E6)
val LightPrimary = Color(0xFF0E7490)
val LightSecondary = Color(0xFF6D28D9)
val LightTextPrimary = Color(0xFF0B1220)
val LightTextSecondary = Color(0xFF4A5670)

private val DarkColors = darkColorScheme(
    primary = TurboCyan,
    onPrimary = Color(0xFF00212A),
    primaryContainer = Color(0xFF0D3B47),
    onPrimaryContainer = Color(0xFFB6F3FF),
    secondary = TurboViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33215C),
    onSecondaryContainer = Color(0xFFE7DCFF),
    tertiary = TurboGreen,
    onTertiary = Color(0xFF00281A),
    background = TurboBackground,
    onBackground = TurboTextPrimary,
    surface = TurboSurface,
    onSurface = TurboTextPrimary,
    surfaceVariant = TurboSurfaceVariant,
    onSurfaceVariant = TurboTextSecondary,
    outline = TurboOutline,
    error = TurboRed,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val TurboTypography = Typography(
    displaySmall = Typography().displaySmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
    ),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun TurboCacheTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TurboTypography,
        content = content,
    )
}

/** Gradient colours used by the primary "one-tap" call to action. */
val TurboCleanGradient = listOf(TurboCyan, TurboViolet)
val TurboScanGradient = listOf(Color(0xFF2563EB), TurboViolet)
