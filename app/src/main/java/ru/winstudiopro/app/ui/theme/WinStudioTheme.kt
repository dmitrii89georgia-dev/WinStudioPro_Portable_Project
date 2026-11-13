package ru.winstudiopro.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.winstudiopro.app.domain.model.AppThemeMode

/**
 * Палитра интерфейса в стиле iOS / Apple с поддержкой светлой и тёмной тем.
 */
@Immutable
data class WinStudioPalette(
    val isDark: Boolean,
    val background: Color,
    val secondaryBackground: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val floatingGlass: Color,
    val floatingBorder: Color,
    val canvasBg: Color,
    val canvasGridLine: Color,
    val accentBlue: Color,
    val accentTeal: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentRed: Color,
    val accentIndigo: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val segmentedBg: Color,
    val segmentedThumb: Color,
    val dimCapsuleBg: Color,
    val dimCapsuleText: Color,
    val dimLineColor: Color,
    val costBarBg: Color,
    val costBarSideBg: Color,
    val dockBg: Color
)

val LightWinStudioPalette = WinStudioPalette(
    isDark = false,
    background = Color(0xFFF2F2F7),
    secondaryBackground = Color(0xFFE5E5EA),
    cardSurface = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFFFFFFF),
    floatingGlass = Color(0xEBFFFFFF),
    floatingBorder = Color(0x18000000),
    canvasBg = Color(0xFFECEFF4),
    canvasGridLine = Color(0x0D1E293B),
    accentBlue = Color(0xFF007AFF),
    accentTeal = Color(0xFF0099CC),
    accentGreen = Color(0xFF34C759),
    accentOrange = Color(0xFFFF9500),
    accentRed = Color(0xFFFF3B30),
    accentIndigo = Color(0xFF5856D6),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6E6E73),
    divider = Color(0x1F3C3C43),
    segmentedBg = Color(0x1E767680),
    segmentedThumb = Color(0xFFFFFFFF),
    dimCapsuleBg = Color(0xFFFFFFFF),
    dimCapsuleText = Color(0xFF1C1C1E),
    dimLineColor = Color(0xFF3A3A3C),
    costBarBg = Color(0xFFF8F9FB),
    costBarSideBg = Color(0xFFEFF2F7),
    dockBg = Color(0xF5FFFFFF)
)

val DarkWinStudioPalette = WinStudioPalette(
    isDark = true,
    background = Color(0xFF000000),
    secondaryBackground = Color(0xFF121316),
    cardSurface = Color(0xFF1C1C1E),
    elevatedSurface = Color(0xFF2C2C2E),
    floatingGlass = Color(0xE61C1C1E),
    floatingBorder = Color(0x26FFFFFF),
    canvasBg = Color(0xFF16181D),
    canvasGridLine = Color(0x10FFFFFF),
    accentBlue = Color(0xFF0A84FF),
    accentTeal = Color(0xFF32ADE6),
    accentGreen = Color(0xFF30D158),
    accentOrange = Color(0xFFFF9F0A),
    accentRed = Color(0xFFFF453A),
    accentIndigo = Color(0xFF5E5CE6),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF98989D),
    divider = Color(0x38545458),
    segmentedBg = Color(0x3D767680),
    segmentedThumb = Color(0xFF636366),
    dimCapsuleBg = Color(0xFF2C2C2E),
    dimCapsuleText = Color(0xFFF5F5F7),
    dimLineColor = Color(0xFFAEAEB2),
    costBarBg = Color(0xFF1C1C1E),
    costBarSideBg = Color(0xFF262629),
    dockBg = Color(0xF51C1C1E)
)

val LocalWinStudioPalette = staticCompositionLocalOf { LightWinStudioPalette }

@Composable
fun WinStudioTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val target = if (themeMode == AppThemeMode.DARK) DarkWinStudioPalette else LightWinStudioPalette
    val animDuration = 220

    val bg by animateColorAsState(target.background, tween(animDuration), label = "bg")
    val card by animateColorAsState(target.cardSurface, tween(animDuration), label = "card")
    val canvas by animateColorAsState(target.canvasBg, tween(animDuration), label = "canvas")
    val textPrim by animateColorAsState(target.textPrimary, tween(animDuration), label = "textPrim")

    val animatedPalette = target.copy(
        background = bg,
        cardSurface = card,
        canvasBg = canvas,
        textPrimary = textPrim
    )

    val m3Colors = if (target.isDark) {
        darkColorScheme(
            primary = target.accentBlue,
            onPrimary = Color.White,
            secondary = target.accentTeal,
            onSecondary = Color.White,
            background = bg,
            onBackground = textPrim,
            surface = card,
            onSurface = textPrim,
            surfaceVariant = target.elevatedSurface,
            onSurfaceVariant = target.textSecondary,
            outline = target.divider,
            error = target.accentRed
        )
    } else {
        lightColorScheme(
            primary = target.accentBlue,
            onPrimary = Color.White,
            secondary = target.accentTeal,
            onSecondary = Color.White,
            background = bg,
            onBackground = textPrim,
            surface = card,
            onSurface = textPrim,
            surfaceVariant = target.secondaryBackground,
            onSurfaceVariant = target.textSecondary,
            outline = target.divider,
            error = target.accentRed
        )
    }

    CompositionLocalProvider(LocalWinStudioPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = m3Colors,
            typography = Typography(),
            content = content
        )
    }
}
