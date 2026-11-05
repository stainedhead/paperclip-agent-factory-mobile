package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

private const val STACK_FONT_SCALE = 1.3f

/**
 * The buttons of a card or a bar: side by side and sharing the width at normal sizes, stacked and full width when the
 * font is scaled up, so no label is cut to a letter per line (WCAG 2.2 reflow). Each action draws one button and is
 * given the modifier that sizes it.
 */
@Composable
fun ActionRow(
    actions: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.medium,
) {
    if (actions.isEmpty()) return
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (action in actions) action(Modifier)
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            for (action in actions) Share(action)
        }
    }
}

@Composable
private fun RowScope.Share(action: @Composable (Modifier) -> Unit) = action(Modifier.weight(1f))
