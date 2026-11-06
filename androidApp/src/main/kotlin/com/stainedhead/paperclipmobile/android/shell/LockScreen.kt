package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.withResumed
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes

private val LockGlyph = 56.dp

/**
 * The app lock (PRD FR-014). It asks the shared app holder to unlock once when it appears and the activity is resumed
 * (the prompt can only be shown to a resumed activity, so a cold start never asks too early); if that is cancelled or
 * fails it stays, says why, and offers the button. The decision to lock and the unlock itself are shared Kotlin: this
 * screen only sends the intent.
 */
@Composable
fun LockScreen(
    actions: LockActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val unlock = rememberUpdatedState(actions.onUnlock)
    LaunchedEffect(lifecycle) { lifecycle.withResumed { unlock.value() } }
    Column(
        modifier.fillMaxSize().padding(Spacing.page).testTag("lock-screen"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            Modifier.size(LockGlyph),
            tint = colors.accent,
        )
        Text(
            stringResource(R.string.lock_title),
            Modifier.semantics { heading() },
            style = AppType.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.lock_message),
            style = AppType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        actions.issue?.let { issue ->
            Text(
                stringResource(issue.stringRes()),
                Modifier.testTag("unlock-issue"),
                style = AppType.body,
                color = colors.destructive,
                textAlign = TextAlign.Center,
            )
        }
        AppButton(
            stringResource(R.string.lock_unlock),
            actions.onUnlock,
            Modifier.testTag("unlock-button"),
            icon = painterResource(R.drawable.ic_lock),
        )
    }
}
