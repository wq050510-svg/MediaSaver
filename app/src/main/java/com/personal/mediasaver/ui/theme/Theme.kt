package com.personal.mediasaver.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personal.mediasaver.domain.model.Platform
import com.personal.mediasaver.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F295C),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C3A57),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF6),
    onSurfaceVariant = Color(0xFF526075),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF6),
    surfaceContainerHighest = Color(0xFFDCE4EF),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF082047),
    primaryContainer = Color(0xFF1A355E),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF06293D),
    secondaryContainer = Color(0xFF12364A),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF042F21),
    tertiaryContainer = Color(0xFF0E3A2F),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0B0F17),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1D2636),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0E131D),
    surfaceContainerLow = Color(0xFF141B27),
    surfaceContainer = Color(0xFF1A2232),
    surfaceContainerHigh = Color(0xFF222C3F),
    surfaceContainerHighest = Color(0xFF2B364D),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF222D40),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3E181C),
    onErrorContainer = Color(0xFFFECACA),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

data class PlatformAccent(
    val dot: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
fun platformAccent(platform: Platform, darkTheme: Boolean): PlatformAccent {
    return when (platform) {
        Platform.DOUYIN -> if (darkTheme) {
            PlatformAccent(Color(0xFFFB7185), Color(0xFF3A1824), Color(0xFFFECDD3))
        } else {
            PlatformAccent(Color(0xFFE11D48), Color(0xFFFFE4E6), Color(0xFF9F1239))
        }
        Platform.BILIBILI -> if (darkTheme) {
            PlatformAccent(Color(0xFFF472B6), Color(0xFF3C1930), Color(0xFFFBCFE8))
        } else {
            PlatformAccent(Color(0xFFFB7299), Color(0xFFFCE7F3), Color(0xFF9D174D))
        }
        Platform.X -> if (darkTheme) {
            PlatformAccent(Color(0xFFE2E8F0), Color(0xFF263042), Color(0xFFE2E8F0))
        } else {
            PlatformAccent(Color(0xFF1E293B), Color(0xFFE2E8F0), Color(0xFF0F172A))
        }
        Platform.INSTAGRAM -> if (darkTheme) {
            PlatformAccent(Color(0xFFC084FC), Color(0xFF321B47), Color(0xFFE9D5FF))
        } else {
            PlatformAccent(Color(0xFF9333EA), Color(0xFFF3E8FF), Color(0xFF6B21A8))
        }
        Platform.UNKNOWN -> if (darkTheme) {
            PlatformAccent(Color(0xFF94A3B8), Color(0xFF1F2937), Color(0xFFCBD5E1))
        } else {
            PlatformAccent(Color(0xFF64748B), Color(0xFFE2E8F0), Color(0xFF334155))
        }
    }
}

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MediaSaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
