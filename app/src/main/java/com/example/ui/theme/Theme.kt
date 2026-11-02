package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.repository.ThemeOption

data class OtakuThemePalette(
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val accentHighlight: Color,
    val isDark: Boolean
)

val LocalOtakuPalette = staticCompositionLocalOf {
    OtakuThemePalette(
        cardGradientStart = ColorNightBlueCardStart,
        cardGradientEnd = ColorNightBlueCardEnd,
        accentHighlight = ColorNightBluePrimary,
        isDark = true
    )
}

fun getSchemeForTheme(option: ThemeOption): Pair<ColorScheme, OtakuThemePalette> {
    return when (option) {
        ThemeOption.FOLLOW_SYSTEM -> {
            val scheme = darkColorScheme(
                primary = ColorNightBluePrimary,
                onPrimary = Color.White,
                primaryContainer = ColorNightBlueSurfaceVariant,
                onPrimaryContainer = ColorNightBlueText,
                secondary = ColorNightBluePrimary,
                onSecondary = Color.White,
                background = ColorNightBlueBg,
                surface = ColorNightBlueSurface,
                surfaceVariant = ColorNightBlueSurfaceVariant,
                onBackground = ColorNightBlueText,
                onSurface = ColorNightBlueText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = ColorNightBlueCardStart,
                cardGradientEnd = ColorNightBlueCardEnd,
                accentHighlight = ColorNightBluePrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
        ThemeOption.NOITE_AZUL -> {
            val scheme = darkColorScheme(
                primary = ColorNightBluePrimary,
                onPrimary = Color.White,
                primaryContainer = ColorNightBlueSurfaceVariant,
                onPrimaryContainer = ColorNightBlueText,
                secondary = ColorNightBluePrimary,
                onSecondary = Color.White,
                background = ColorNightBlueBg,
                surface = ColorNightBlueSurface,
                surfaceVariant = ColorNightBlueSurfaceVariant,
                onBackground = ColorNightBlueText,
                onSurface = ColorNightBlueText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = ColorNightBlueCardStart,
                cardGradientEnd = ColorNightBlueCardEnd,
                accentHighlight = ColorNightBluePrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
        ThemeOption.AZUL_BRANCO -> {
            val scheme = lightColorScheme(
                primary = ColorBlueWhitePrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF1E40AF),
                secondary = Color(0xFF3B82F6),
                onSecondary = Color.White,
                background = ColorBlueWhiteBg,
                surface = ColorBlueWhiteSurface,
                surfaceVariant = ColorBlueWhiteSurfaceVariant,
                onBackground = ColorBlueWhiteText,
                onSurface = ColorBlueWhiteText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFFE2ECFF),
                cardGradientEnd = Color(0xFFC7DBFF),
                accentHighlight = ColorBlueWhitePrimary,
                isDark = false
            )
            Pair(scheme, palette)
        }
        ThemeOption.SAKURA -> {
            val scheme = darkColorScheme(
                primary = ColorSakuraPrimary,
                onPrimary = Color.White,
                primaryContainer = ColorSakuraSurfaceVariant,
                onPrimaryContainer = ColorSakuraText,
                secondary = ColorSakuraPrimary,
                onSecondary = Color.White,
                background = ColorSakuraBg,
                surface = ColorSakuraSurface,
                surfaceVariant = ColorSakuraSurfaceVariant,
                onBackground = ColorSakuraText,
                onSurface = ColorSakuraText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFF3B1E34),
                cardGradientEnd = Color(0xFF261522),
                accentHighlight = ColorSakuraPrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
        ThemeOption.ESMERALDA -> {
            val scheme = darkColorScheme(
                primary = ColorEmeraldPrimary,
                onPrimary = Color.White,
                primaryContainer = ColorEmeraldSurfaceVariant,
                onPrimaryContainer = ColorEmeraldText,
                secondary = ColorEmeraldPrimary,
                onSecondary = Color.White,
                background = ColorEmeraldBg,
                surface = ColorEmeraldSurface,
                surfaceVariant = ColorEmeraldSurfaceVariant,
                onBackground = ColorEmeraldText,
                onSurface = ColorEmeraldText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFF1A3B32),
                cardGradientEnd = Color(0xFF122822),
                accentHighlight = ColorEmeraldPrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
        ThemeOption.MEIA_NOITE_VIOLETA -> {
            val scheme = darkColorScheme(
                primary = ColorVioletPrimary,
                onPrimary = Color.White,
                primaryContainer = ColorVioletSurfaceVariant,
                onPrimaryContainer = ColorVioletText,
                secondary = ColorVioletPrimary,
                onSecondary = Color.White,
                background = ColorVioletBg,
                surface = ColorVioletSurface,
                surfaceVariant = ColorVioletSurfaceVariant,
                onBackground = ColorVioletText,
                onSurface = ColorVioletText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFF2B2257),
                cardGradientEnd = Color(0xFF1D173A),
                accentHighlight = ColorVioletPrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
        ThemeOption.CAFE_CREME -> {
            val scheme = lightColorScheme(
                primary = ColorCoffeePrimary,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEFE2D3),
                onPrimaryContainer = Color(0xFF4A2810),
                secondary = ColorCoffeePrimary,
                onSecondary = Color.White,
                background = ColorCoffeeBg,
                surface = ColorCoffeeSurface,
                surfaceVariant = ColorCoffeeSurfaceVariant,
                onBackground = ColorCoffeeText,
                onSurface = ColorCoffeeText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFFEFE2D3),
                cardGradientEnd = Color(0xFFDFCCB7),
                accentHighlight = ColorCoffeePrimary,
                isDark = false
            )
            Pair(scheme, palette)
        }
        ThemeOption.NEON_CYBER -> {
            val scheme = darkColorScheme(
                primary = ColorCyberPrimary,
                onPrimary = Color.Black,
                primaryContainer = ColorCyberSurfaceVariant,
                onPrimaryContainer = ColorCyberText,
                secondary = ColorCyberSecondary,
                onSecondary = Color.White,
                background = ColorCyberBg,
                surface = ColorCyberSurface,
                surfaceVariant = ColorCyberSurfaceVariant,
                onBackground = ColorCyberText,
                onSurface = ColorCyberText
            )
            val palette = OtakuThemePalette(
                cardGradientStart = Color(0xFF16233B),
                cardGradientEnd = Color(0xFF0E1626),
                accentHighlight = ColorCyberPrimary,
                isDark = true
            )
            Pair(scheme, palette)
        }
    }
}

@Composable
fun OtakuVerseTheme(
    themeOption: ThemeOption = ThemeOption.NOITE_AZUL,
    content: @Composable () -> Unit
) {
    val (colorScheme, palette) = getSchemeForTheme(themeOption)
    CompositionLocalProvider(LocalOtakuPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    OtakuVerseTheme(content = content)
}
