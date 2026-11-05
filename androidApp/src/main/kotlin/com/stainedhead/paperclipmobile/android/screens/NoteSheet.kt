package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue

private val LockGlyph = 40.dp
private val SmallGlyph = 16.dp

/** What a note sheet says: every word comes from the screen that opens it, as resources. */
class NoteSheetText(
    val title: String,
    val noteLabel: String,
    val placeholder: String,
    val help: String,
    val confirm: String,
    val subtitle: String? = null,
)

/**
 * The note a reject or a revision request needs. It is open while the shared state carries a prompt; Confirm sends the
 * note and the shared use case does the rest: a destructive one (a reject) asks the device owner before it calls the
 * server and a declined prompt comes back as [deviceAuthIssue] with the sheet staying open; this sheet never asks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    text: NoteSheetText,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean,
    isWorking: Boolean,
    deviceAuthIssue: DeviceAuthIssue?,
    noteIssue: String?,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) { NoteContent(text, onConfirm, onCancel, destructive, isWorking, deviceAuthIssue, noteIssue) }
}

@Composable
fun NoteContent(
    text: NoteSheetText,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean,
    isWorking: Boolean,
    deviceAuthIssue: DeviceAuthIssue?,
    noteIssue: String?,
) {
    var note by rememberSaveable { mutableStateOf("") }
    val colors = AppTheme.colors
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(Spacing.page)
            .navigationBarsPadding()
            .testTag("note-sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoteHeader(text, destructive)
        FormField(
            title = text.noteLabel,
            value = note,
            onValueChange = { note = it },
            tag = "note-field",
            placeholder = text.placeholder,
            help = text.help,
            issue = noteIssue,
            capitalization = KeyboardCapitalization.Sentences,
        )
        if (destructive) AuthLine(isWorking)
        deviceAuthIssue?.let {
            Text(
                stringResource(it.stringRes()),
                Modifier.testTag("note-issue"),
                style = AppType.body,
                color = colors.destructive,
                textAlign = TextAlign.Center,
            )
        }
        AppButton(
            text.confirm,
            { onConfirm(note) },
            Modifier.testTag("note-confirm"),
            kind = if (destructive) ButtonKind.Destructive else ButtonKind.Primary,
            enabled = !isWorking,
            icon = if (destructive) painterResource(R.drawable.ic_lock) else null,
        )
        AppButton(
            stringResource(R.string.common_cancel),
            onCancel,
            Modifier.testTag("note-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}

/** The lock glyph of a destructive note, then the title and what the note is about. */
@Composable
private fun NoteHeader(
    text: NoteSheetText,
    destructive: Boolean,
) {
    val colors = AppTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (destructive) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(LockGlyph).testTag("note-lock"),
                tint = colors.destructive,
            )
        }
        Text(
            text.title,
            Modifier.semantics { heading() },
            style = AppType.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        text.subtitle?.let {
            Text(it, style = AppType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** What follows the tap, in words: the device owner is asked, or the answer is awaited. */
@Composable
private fun AuthLine(isWorking: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            Modifier.size(SmallGlyph),
            tint = AppTheme.colors.textSecondary,
        )
        Text(
            stringResource(if (isWorking) R.string.confirm_destructive_working else R.string.confirm_destructive_auth),
            style = AppType.secondary,
            color = AppTheme.colors.textSecondary,
        )
    }
}
