package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The colours Material 3 has no role for (hairlines, soft tones), resolved for the theme in use. */
@Immutable
class AppColors(
    private val darkTheme: Boolean,
) {
    val background: Color = Palette.background.color(darkTheme)
    val surface: Color = Palette.surface.color(darkTheme)
    val muted: Color = Palette.muted.color(darkTheme)
    val hairline: Color = Palette.hairline.color(darkTheme)
    val border: Color = Palette.border.color(darkTheme)
    val textPrimary: Color = Palette.textPrimary.color(darkTheme)
    val textSecondary: Color = Palette.textSecondary.color(darkTheme)
    val accent: Color = Palette.accent.color(darkTheme)
    val onAccent: Color = Palette.onAccent.color(darkTheme)
    val accentSoft: Color = Palette.accentSoft.color(darkTheme)
    val destructive: Color = Palette.destructive.color(darkTheme)
    val destructiveSoft: Color = Palette.destructiveSoft.color(darkTheme)
    val warn: Color = Palette.warn.color(darkTheme)
    val warnSoft: Color = Palette.warnSoft.color(darkTheme)
    val success: Color = Palette.success.color(darkTheme)
    val successSoft: Color = Palette.successSoft.color(darkTheme)

    val isDark: Boolean get() = darkTheme
}

private val LocalAppColors = staticCompositionLocalOf { AppColors(darkTheme = false) }

/** The mocks' type scale; sizes are `sp`, so every size grows with the user's font setting (up to 200%). */
object AppType {
    val largeTitle = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val cardTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val button = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val secondary = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val micro = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
}

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

private fun scheme(
    colors: AppColors,
    dark: Boolean,
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.muted,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.muted,
        outline = colors.border,
        outlineVariant = colors.hairline,
        error = colors.destructive,
        onError = colors.onAccent,
        errorContainer = colors.destructiveSoft,
        onErrorContainer = colors.destructive,
    )
}

private val MaterialType =
    Typography(
        headlineLarge = AppType.largeTitle,
        titleLarge = AppType.title,
        titleMedium = AppType.cardTitle,
        bodyLarge = AppType.body,
        bodyMedium = AppType.body,
        labelLarge = AppType.button,
        bodySmall = AppType.secondary,
        labelMedium = AppType.label,
        labelSmall = AppType.micro,
    )

/**
 * The app theme (AND-015): the mocks' tokens as a Material 3 colour scheme, light or dark. [darkTheme] is a parameter,
 * so screenshots and tests do not depend on the host. Dynamic colour for neutrals is added in the polish milestone.
 */
@Composable
fun PaperclipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = AppColors(darkTheme)
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors, darkTheme), typography = MaterialType, content = content)
    }
}
