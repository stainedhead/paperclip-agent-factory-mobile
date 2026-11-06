package com.stainedhead.paperclipmobile.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue

private val LockGlyph = 40.dp
private val SmallGlyph = 16.dp

/**
 * A confirmation for an action that does not ask for the device owner (sign out, revoke a key): title, what happens,
 * the two buttons. It carries no lock glyph, because no prompt follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    isWorking: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) { ConfirmContent(title, message, confirmTitle, onConfirm, onCancel, isWorking = isWorking) }
}

@Composable
fun ConfirmContent(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
) {
    val colors = AppTheme.colors
    Column(
        modifier.padding(Spacing.page).navigationBarsPadding().testTag("confirm-sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            Modifier.semantics {
                heading()
            },
            style = AppType.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(message, style = AppType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        AppButton(confirmTitle, onConfirm, Modifier.testTag("confirm-sheet-confirm"), enabled = !isWorking)
        AppButton(
            stringResource(R.string.common_cancel),
            onCancel,
            Modifier.testTag("confirm-sheet-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}

/**
 * The confirmation every destructive action goes through (pause an agent, cancel a run, reject, a destructive decision
 * option, a budget raise). It shows the lock glyph so the person knows the device owner is asked after the tap.
 *
 * The prompt itself is not made here. [onConfirm] sends the screen's confirm intent, and the shared use case asks the
 * `Biometrics` port before it calls the server; if the person cancels or fails, no call is made and the state holder
 * reports the [DeviceAuthIssue], which this sheet shows through [issue]. One gate for every platform, and a screen can
 * never skip it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDestructiveSheet(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    issue: DeviceAuthIssue? = null,
    isWorking: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        ConfirmDestructiveContent(
            title,
            message,
            confirmTitle,
            onConfirm,
            onCancel,
            issue = issue,
            isWorking = isWorking,
        )
    }
}

@Composable
fun ConfirmDestructiveContent(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    issue: DeviceAuthIssue? = null,
    isWorking: Boolean = false,
) {
    val colors = AppTheme.colors
    Column(
        modifier.padding(Spacing.page).navigationBarsPadding().testTag("confirm-destructive-sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(LockGlyph).testTag("confirm-destructive-lock"),
            tint = colors.destructive,
        )
        Text(
            title,
            Modifier.semantics {
                heading()
            },
            style = AppType.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(message, style = AppType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        AuthLine(isWorking)
        if (issue != null) {
            Text(
                stringResource(issue.stringRes()),
                Modifier.testTag("confirm-destructive-issue"),
                style = AppType.body,
                color = colors.destructive,
                textAlign = TextAlign.Center,
            )
        }
        AppButton(
            confirmTitle,
            onConfirm,
            Modifier.testTag("confirm-destructive-confirm"),
            kind = ButtonKind.Destructive,
            enabled = !isWorking,
            icon = painterResource(R.drawable.ic_lock),
        )
        AppButton(
            stringResource(R.string.common_cancel),
            onCancel,
            Modifier.testTag("confirm-destructive-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}

/** What follows the tap, in words: the device owner is asked, or the answer is awaited. */
@Composable
private fun AuthLine(isWorking: Boolean) {
    val colors = AppTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            Modifier.size(SmallGlyph),
            tint = colors.textSecondary,
        )
        Text(
            stringResource(if (isWorking) R.string.confirm_destructive_working else R.string.confirm_destructive_auth),
            style = AppType.secondary,
            color = colors.textSecondary,
        )
    }
}
