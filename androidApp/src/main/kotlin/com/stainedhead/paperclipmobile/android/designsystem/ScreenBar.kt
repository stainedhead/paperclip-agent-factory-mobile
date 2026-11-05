package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.stainedhead.paperclipmobile.android.R

/** A bar icon: at least 48 dp square, described for TalkBack, never text-less. */
@Composable
fun BarIconButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.accent,
) {
    IconButton(onClick, modifier.testTag(tag)) { Icon(icon, contentDescription = description, tint = tint) }
}

/**
 * The title of a screen with the actions it offers: a back arrow when it was pushed (the system back does the same),
 * the title as a heading, and [actions] on the trailing side. A tab root has no back arrow and a large title.
 */
@Composable
fun ScreenTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    isLarge: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = Spacing.minimumTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (onBack != null) {
            BarIconButton(
                painterResource(R.drawable.ic_back),
                stringResource(R.string.common_back),
                onBack,
                "back-button",
                tint = AppTheme.colors.textPrimary,
            )
        }
        Text(
            title,
            Modifier.weight(1f).semantics { heading() }.testTag("screen-title"),
            style = if (isLarge) AppType.largeTitle else AppType.title,
            color = AppTheme.colors.textPrimary,
        )
        actions()
    }
}
