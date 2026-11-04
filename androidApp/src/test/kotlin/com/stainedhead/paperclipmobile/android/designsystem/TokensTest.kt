package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.ui.unit.dp
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** AND-015: the tokens come from the design mocks and keep text readable in light and dark. */
class TokensTest {
    @Test
    fun `the mocks brand values are the light tokens`() {
        Palette.background.light shouldBe 0xF4F4F1
        Palette.surface.light shouldBe 0xFFFFFF
        Palette.textPrimary.light shouldBe 0x15171C
        Palette.textSecondary.light shouldBe 0x5B606B
        Palette.accent.light shouldBe 0x2649C9
        Palette.destructive.light shouldBe 0xB3261E
        Palette.warn.light shouldBe 0xA84300
        Palette.success.light shouldBe 0x1F7A4D
        Palette.hairline.light shouldBe 0xE3E3DE
    }

    private val readablePairs =
        listOf(
            Triple("primary on background", Palette.textPrimary, Palette.background),
            Triple("primary on surface", Palette.textPrimary, Palette.surface),
            Triple("secondary on background", Palette.textSecondary, Palette.background),
            Triple("secondary on surface", Palette.textSecondary, Palette.surface),
            Triple("accent on surface", Palette.accent, Palette.surface),
            Triple("onAccent on accent", Palette.onAccent, Palette.accent),
            Triple("destructive on surface", Palette.destructive, Palette.surface),
            Triple("destructive on destructiveSoft", Palette.destructive, Palette.destructiveSoft),
            Triple("warn on surface", Palette.warn, Palette.surface),
            Triple("warn on warnSoft", Palette.warn, Palette.warnSoft),
            Triple("success on surface", Palette.success, Palette.surface),
            Triple("success on successSoft", Palette.success, Palette.successSoft),
            Triple("primary on muted", Palette.textPrimary, Palette.muted),
            Triple("secondary on muted", Palette.textSecondary, Palette.muted),
        )

    @Test
    fun `text pairs meet WCAG AA in light and dark`() {
        for ((name, foreground, background) in readablePairs) {
            withClue("light: $name") {
                Contrast.ratio(foreground.light, background.light) shouldBeGreaterThanOrEqual MIN_TEXT_CONTRAST
            }
            withClue("dark: $name") {
                Contrast.ratio(foreground.dark, background.dark) shouldBeGreaterThanOrEqual MIN_TEXT_CONTRAST
            }
        }
    }

    @Test
    fun `contrast matches the known reference values`() {
        (abs(Contrast.ratio(0x000000, 0xFFFFFF) - 21.0) < TOLERANCE) shouldBe true
        (abs(Contrast.ratio(0xFFFFFF, 0xFFFFFF) - 1.0) < TOLERANCE) shouldBe true
    }

    @Test
    fun `spacing and radius come from the mocks and the smallest target is 48 dp`() {
        Spacing.page shouldBe 20.dp
        Spacing.card shouldBe 14.dp
        Radius.card shouldBe 14.dp
        Radius.button shouldBe 10.dp
        Radius.chip shouldBe 16.dp
        Spacing.minimumTarget shouldBe 48.dp
    }

    private fun withClue(
        clue: String,
        block: () -> Unit,
    ) = io.kotest.assertions.withClue(clue, block)

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
        const val TOLERANCE = 0.01
    }
}
