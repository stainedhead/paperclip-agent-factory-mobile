package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

private const val OPAQUE = 0xFF000000

/**
 * A colour role with its light and dark value as 0xRRGGBB. Light values are the design mocks' own
 * (`Paperclip-Remote-mobile-UI-design.html`); dark values keep the same roles and are checked for contrast in tests.
 */
class Token(
    val light: Int,
    val dark: Int,
) {
    fun color(darkTheme: Boolean): Color = Color(OPAQUE or (if (darkTheme) dark else light).toLong())
}

object Palette {
    val background = Token(light = 0xF4F4F1, dark = 0x111316)
    val surface = Token(light = 0xFFFFFF, dark = 0x1C1F24)
    val muted = Token(light = 0xF0F0EC, dark = 0x2A2E35)
    val hairline = Token(light = 0xE3E3DE, dark = 0x2F333B)
    val border = Token(light = 0xD6D6D0, dark = 0x454A54)
    val textPrimary = Token(light = 0x15171C, dark = 0xF2F3F5)
    val textSecondary = Token(light = 0x5B606B, dark = 0xA9AEB9)
    val accent = Token(light = 0x2649C9, dark = 0x8FA5F7)
    val onAccent = Token(light = 0xFFFFFF, dark = 0x0E1534)
    val accentSoft = Token(light = 0xE6EBFA, dark = 0x1F2646)
    val destructive = Token(light = 0xB3261E, dark = 0xFF9A92)
    val destructiveSoft = Token(light = 0xFBE9E7, dark = 0x3B1D1B)
    val warn = Token(light = 0xA84300, dark = 0xFFB37F)
    val warnSoft = Token(light = 0xFFF1E5, dark = 0x3A2616)
    val success = Token(light = 0x1F7A4D, dark = 0x6FD39F)
    val successSoft = Token(light = 0xEAF4EE, dark = 0x14291F)
}

/** WCAG 2.x contrast ratio between two sRGB colours given as 0xRRGGBB. */
object Contrast {
    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05
    private const val BYTE_MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    fun ratio(
        first: Int,
        second: Int,
    ): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + FLARE) / (minOf(a, b) + FLARE)
    }

    private fun luminance(hex: Int): Double =
        RED_WEIGHT * channel(hex shr RED_SHIFT) + GREEN_WEIGHT * channel(hex shr GREEN_SHIFT) +
            BLUE_WEIGHT * channel(hex)

    private fun channel(shifted: Int): Double {
        val value = (shifted and BYTE_MASK) / CHANNEL_MAX
        return if (value <= LINEAR_THRESHOLD) {
            value / LINEAR_DIVISOR
        } else {
            ((value + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        }
    }
}

object Spacing {
    val hairline: Dp = 2.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 6.dp
    val medium: Dp = 8.dp
    val large: Dp = 10.dp
    val extraLarge: Dp = 12.dp
    val card: Dp = 14.dp
    val roomy: Dp = 16.dp
    val page: Dp = 20.dp

    /** The smallest tappable size (Material and WCAG 2.2 target size guidance). */
    val minimumTarget: Dp = 48.dp
}

object Radius {
    val dot: Dp = 4.dp
    val small: Dp = 6.dp
    val button: Dp = 10.dp
    val card: Dp = 14.dp
    val chip: Dp = 16.dp
}
