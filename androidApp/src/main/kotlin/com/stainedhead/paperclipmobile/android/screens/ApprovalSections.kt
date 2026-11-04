package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.DisabledReason
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.SectionHeader
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.ApprovalActionUi
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.CommentUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.NotePromptKind

private val ChevronGlyph = 16.dp

/** The issues this approval is about; each opens its issue. */
@Composable
fun LinkedWork(
    state: ApprovalState,
    onOpenIssue: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.approval_linked_work))
        for (issue in state.issues) {
            val title = listOfNotNull(issue.identifier, issue.title).joinToString(" ")
            CardSurface(
                Modifier
                    .clickable(role = Role.Button) { onOpenIssue(issue.id) }
                    .testTag("approval-issue-${issue.id}"),
            ) {
                Row(
                    Modifier.heightIn(min = Spacing.minimumTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(title, Modifier.weight(1f), style = AppType.body, color = AppTheme.colors.textPrimary)
                    issue.status?.let {
                        Text(
                            prettify(it).replaceFirstChar { c -> c.uppercase() },
                            style = AppType.secondary,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        Modifier.size(ChevronGlyph),
                        tint = AppTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** The comments under the approval, and the box to add one when the person may comment. */
@Composable
fun Discussion(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SectionHeader(stringResource(R.string.approval_discussion))
        if (state.comments.isEmpty()) {
            Text(
                stringResource(R.string.approval_no_discussion),
                style = AppType.body,
                color = AppTheme.colors.textSecondary,
            )
        }
        for (comment in state.comments) CommentCard(comment)
        if (ApprovalActionUi.Comment in state.actions) CommentBox(state, send)
    }
}

@Composable
private fun CommentCard(comment: CommentUi) {
    val author =
        stringResource(if (comment.isFromAgent) R.string.approval_comment_agent else R.string.approval_comment_you)
    CardSurface(Modifier.testTag("approval-comment-${comment.id}")) {
        Text(author, style = AppType.label, color = AppTheme.colors.textSecondary)
        Text(comment.body, style = AppType.body, color = AppTheme.colors.textPrimary)
    }
}

/** The person's own posted comment clears the box, never a comment that arrives from someone else. */
@Composable
private fun CommentBox(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(state.commentPostedCount) { text = "" }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        FormField(
            title = stringResource(R.string.approval_comment_label),
            value = text,
            onValueChange = { text = it },
            tag = "approval-comment-field",
            placeholder = stringResource(R.string.approval_comment_placeholder),
            capitalization = KeyboardCapitalization.Sentences,
        )
        AppButton(
            stringResource(R.string.approval_comment_post),
            { send(ApprovalIntent.CommentSubmitted(text)) },
            Modifier.testTag("approval-comment-post"),
            kind = ButtonKind.Secondary,
            enabled = text.isNotBlank() && !state.isSubmitting,
        )
    }
}

private val mainActions = listOf(ApprovalActionUi.Approve, ApprovalActionUi.Reject, ApprovalActionUi.RequestRevision)

/** The bar under the content: why actions are off, Reject (lock glyph) and Approve, then Request revision. */
@Composable
fun ApprovalActionBar(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
) {
    val hasMain = mainActions.any { it in state.actions }
    if (state.phase != DetailPhase.Content || (state.actionsDisabledReason == null && !hasMain)) return
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).testTag("approval-action-bar")) {
        HairlineDivider()
        Column(
            Modifier
                .padding(start = Spacing.page, end = Spacing.page, top = Spacing.extraLarge, bottom = Spacing.medium)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            state.actionsDisabledReason?.let { DisabledReason(it) }
            ActionRow(
                buildList<@Composable (Modifier) -> Unit> {
                    if (ApprovalActionUi.Reject in state.actions) {
                        add { m ->
                            AppButton(
                                stringResource(ApprovalActionUi.Reject.stringRes()),
                                { send(ApprovalIntent.RejectTapped) },
                                m.testTag("approval-reject"),
                                kind = ButtonKind.Destructive,
                                icon = painterResource(R.drawable.ic_lock),
                            )
                        }
                    }
                    if (ApprovalActionUi.Approve in state.actions) {
                        add { m ->
                            AppButton(
                                stringResource(ApprovalActionUi.Approve.stringRes()),
                                { send(ApprovalIntent.ApproveTapped) },
                                m.testTag("approval-approve"),
                            )
                        }
                    }
                },
            )
            if (ApprovalActionUi.RequestRevision in state.actions) {
                AppButton(
                    stringResource(ApprovalActionUi.RequestRevision.stringRes()),
                    { send(ApprovalIntent.RequestRevisionTapped) },
                    Modifier.testTag("approval-request-revision"),
                    kind = ButtonKind.Muted,
                )
            }
        }
    }
}

/** The note a reject or a revision request needs; a reject asks the device owner after Confirm, in shared code. */
@Composable
fun ApprovalNoteSheet(
    state: ApprovalState,
    send: (ApprovalIntent) -> Unit,
) {
    val kind = state.notePrompt ?: return
    val reject = kind == NotePromptKind.Reject
    NoteSheet(
        text =
            NoteSheetText(
                title = stringResource(if (reject) R.string.approval_reject_title else R.string.approval_revise_title),
                noteLabel = stringResource(R.string.approval_note_label),
                placeholder =
                    stringResource(
                        if (reject) R.string.approval_reject_placeholder else R.string.approval_revise_placeholder,
                    ),
                help =
                    stringResource(
                        if (reject) R.string.approval_reject_message else R.string.approval_revise_message,
                    ),
                confirm =
                    stringResource(
                        if (reject) R.string.approval_reject_confirm else R.string.approval_revise_confirm,
                    ),
            ),
        onConfirm = { send(ApprovalIntent.NoteConfirmed(it)) },
        onCancel = { send(ApprovalIntent.NoteCancelled) },
        destructive = reject && state.rejectNeedsDeviceAuth,
        isWorking = state.isSubmitting,
        deviceAuthIssue = state.feedback?.deviceAuth,
        noteIssue = state.feedback?.invalid?.let { stringResource(it.stringRes()) },
    )
}
