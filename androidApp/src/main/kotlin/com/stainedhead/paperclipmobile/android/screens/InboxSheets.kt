package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.SnoozePresetUi

/** The presets the state offers (their times are the shared core's), one tap each; nothing is sent before a pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(
    options: List<SnoozePresetUi>,
    onPick: (SnoozePresetUi) -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            Modifier.padding(Spacing.page).navigationBarsPadding().testTag("snooze-sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.inbox_snooze_title),
                Modifier.semantics { heading() },
                style = AppType.title,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            for (preset in options) {
                AppButton(
                    stringResource(preset.stringRes()),
                    { onPick(preset) },
                    Modifier.testTag("snooze-${preset.name}"),
                    kind = ButtonKind.Secondary,
                )
            }
            AppButton(
                stringResource(R.string.common_cancel),
                onCancel,
                Modifier.testTag("snooze-cancel"),
                kind = ButtonKind.Muted,
            )
        }
    }
}

/** The five-second offer to take back a dismiss or snooze: on screen exactly while the state carries `undo`. */
@Composable
fun UndoSnackbar(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .padding(horizontal = Spacing.page, vertical = Spacing.medium)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.button))
            .background(colors.textPrimary)
            .padding(start = Spacing.roomy)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("inbox-undo"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
    ) {
        Text(
            stringResource(R.string.inbox_undo_message),
            Modifier.weight(1f).testTag("inbox-undo-message"),
            style = AppType.body,
            color = colors.background,
        )
        TextButton(onUndo, Modifier.heightIn(min = Spacing.minimumTarget).testTag("inbox-undo-button")) {
            Text(stringResource(R.string.inbox_undo_action), style = AppType.button, color = colors.accentSoft)
        }
    }
}

/** The note a reject needs; the device owner is asked by the shared use case after Confirm, never by this sheet. */
@Composable
fun RejectNoteSheet(
    state: InboxState,
    send: (InboxIntent) -> Unit,
) {
    val prompt = state.rejectPrompt ?: return
    val title =
        state.sections
            .flatMap { it.items }
            .firstOrNull { it.id == prompt.itemId }
            ?.title
    NoteSheet(
        text =
            NoteSheetText(
                title = stringResource(R.string.inbox_reject_title),
                noteLabel = stringResource(R.string.inbox_note_label),
                placeholder = stringResource(R.string.inbox_note_placeholder),
                help = stringResource(R.string.inbox_reject_message),
                confirm = stringResource(R.string.inbox_reject_confirm),
                subtitle = title,
            ),
        onConfirm = { send(InboxIntent.RejectConfirmed(it)) },
        onCancel = { send(InboxIntent.RejectCancelled) },
        destructive = true,
        isWorking = state.isMutating,
        deviceAuthIssue = state.feedback?.deviceAuth,
        noteIssue = state.feedback?.invalid?.let { stringResource(it.stringRes()) },
    )
}
