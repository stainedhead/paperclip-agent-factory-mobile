package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.android.designsystem.foreground

private val OutcomeGlyph = 20.dp

private fun StatusTone.outcomeSymbol(): Int =
    when (this) {
        StatusTone.Success -> R.drawable.ic_check_circle
        StatusTone.Warn -> R.drawable.ic_warning
        StatusTone.Critical -> R.drawable.ic_error
        StatusTone.Neutral -> R.drawable.ic_radio_unchecked
    }

/**
 * The result of one effect or one action: a symbol, a title and the status in words. The symbol never carries the
 * meaning alone, and the row is one element.
 */
@Composable
fun OutcomeRow(
    title: String,
    status: String,
    tone: StatusTone,
    tag: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Icon(
            painterResource(tone.outcomeSymbol()),
            contentDescription = null,
            Modifier.size(OutcomeGlyph),
            tint = tone.foreground(colors),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(title, style = AppType.body, color = colors.textPrimary)
            Text(
                status,
                style = AppType.button,
                color = tone.foreground(colors),
                modifier = Modifier.testTag("$tag-status"),
            )
            if (detail != null) Text(detail, style = AppType.secondary, color = colors.textSecondary)
        }
    }
}
