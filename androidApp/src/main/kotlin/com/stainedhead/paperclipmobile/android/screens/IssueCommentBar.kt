package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssueState

private const val FIELD_MAX_LINES = 4
private val BAR_SHAPE = RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card)

/**
 * The fixed bar of the Issue screen: change status, the comment box, send. The reopen and interrupt switches show only
 * when the state says a comment may do that. The box keeps its text until the holder counts the comment
 * (`commentSentCount`), so a refused or failed send loses nothing and a posted one is never put back.
 */
@Composable
fun IssueCommentBar(
    state: IssueState,
    send: (IssueIntent) -> Unit,
) {
    var comment by rememberSaveable { mutableStateOf("") }
    var reopen by rememberSaveable { mutableStateOf(false) }
    var interrupt by rememberSaveable { mutableStateOf(false) }
    var seen by rememberSaveable { mutableIntStateOf(state.commentSentCount) }
    LaunchedEffect(state.commentSentCount) {
        if (state.commentSentCount != seen) {
            seen = state.commentSentCount
            comment = ""
            reopen = false
            interrupt = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, BAR_SHAPE)
            .padding(horizontal = Spacing.extraLarge, vertical = Spacing.large)
            .navigationBarsPadding()
            .testTag("issue-comment-bar"),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        state.actionsDisabledReason?.let { DisabledReason(it) }
        if (state.canComment && state.canReopen) {
            SwitchRow(stringResource(R.string.issue_reopen_toggle), reopen, "issue-reopen") { reopen = it }
        }
        if (state.canComment && state.canInterrupt) {
            SwitchRow(stringResource(R.string.issue_interrupt_toggle), interrupt, "issue-interrupt") { interrupt = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.Bottom) {
            StatusMenu(state, send)
            CommentField(comment, state.canComment, Modifier.weight(1f)) { comment = it }
            SendButton(state.canComment) { send(IssueIntent.CommentSubmitted(comment, reopen, interrupt)) }
        }
    }
}

@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = AppType.body, color = AppTheme.colors.textPrimary)
        Switch(checked, null)
    }
}

@Composable
private fun StatusMenu(
    state: IssueState,
    send: (IssueIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(Spacing.minimumTarget).testTag("issue-status-menu"),
            enabled = state.canChangeStatus,
            colors =
                IconButtonDefaults.iconButtonColors(
                    contentColor = colors.textPrimary,
                    disabledContentColor = colors.textSecondary,
                ),
        ) {
            Icon(painterResource(R.drawable.ic_snooze), contentDescription = stringResource(R.string.issue_status_menu))
        }
        DropdownMenu(expanded, { expanded = false }) {
            for (status in state.statusOptions) {
                DropdownMenuItem(
                    text = { Text(stringResource(status.stringRes()), style = AppType.body) },
                    onClick = {
                        expanded = false
                        send(IssueIntent.StatusSelected(status))
                    },
                    modifier = Modifier.testTag("issue-status-${status.name}"),
                )
            }
        }
    }
}

@Composable
private fun CommentField(
    value: String,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = Spacing.minimumTarget).testTag("issue-comment-field"),
        enabled = enabled,
        maxLines = FIELD_MAX_LINES,
        textStyle = AppType.body.copy(color = colors.textPrimary),
        placeholder = {
            Text(stringResource(R.string.issue_comment_placeholder), style = AppType.body, color = colors.textSecondary)
        },
        shape = RoundedCornerShape(Radius.chip),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.muted,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
            ),
    )
}

@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(Spacing.minimumTarget)
                .background(if (enabled) colors.accent else colors.muted, CircleShape)
                .testTag("issue-comment-send"),
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = colors.onAccent,
                disabledContentColor = colors.textSecondary,
            ),
    ) {
        Icon(
            painterResource(R.drawable.ic_send),
            contentDescription = stringResource(R.string.issue_comment_send),
            Modifier.size(20.dp),
        )
    }
}

/**
 * "New subtask": a title. The holder checks it and the role; the sheet closes when the holder counts the subtask
 * (`subtaskCreatedCount`), so a refusal or a failure keeps the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtaskSheet(
    state: IssueState,
    send: (IssueIntent) -> Unit,
    onClose: () -> Unit,
) {
    val created = remember { state.subtaskCreatedCount }
    LaunchedEffect(state.subtaskCreatedCount) { if (state.subtaskCreatedCount != created) onClose() }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) { SubtaskForm(state, send, onClose) }
}

@Composable
private fun SubtaskForm(
    state: IssueState,
    send: (IssueIntent) -> Unit,
    onClose: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.page)
            .navigationBarsPadding()
            .imePadding()
            .testTag("issue-subtask-form"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
    ) {
        Text(
            stringResource(R.string.issue_subtask_title),
            Modifier.semantics { heading() },
            style = AppType.title,
            color = AppTheme.colors.textPrimary,
        )
        FormField(
            title = stringResource(R.string.issue_subtask_field),
            value = title,
            onValueChange = { title = it },
            tag = "issue-subtask-title",
            placeholder = stringResource(R.string.issue_subtask_placeholder),
            capitalization = KeyboardCapitalization.Sentences,
        )
        state.feedback?.let { FeedbackBanner(it, { send(IssueIntent.MessageDismissed) }) }
        AppButton(
            stringResource(R.string.issue_subtask_save),
            { send(IssueIntent.SubtaskSubmitted(title)) },
            Modifier.testTag("issue-subtask-save"),
            enabled = !state.isBusy,
        )
        AppButton(
            stringResource(R.string.common_cancel),
            onClose,
            Modifier.testTag("issue-subtask-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}
