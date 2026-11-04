package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.CommentAuthorUi
import com.stainedhead.paperclipmobile.presentation.IssueCommentUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssueState

private const val MILLIS_PER_SECOND = 1_000L

/** The Thread tab: the comments in time order, as the shared state lists them. */
@Composable
fun IssueThread(
    state: IssueState,
    send: (IssueIntent) -> Unit,
) {
    Column(Modifier.testTag("issue-thread"), verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        if (state.interactionsUnavailable) {
            Text(
                stringResource(R.string.issue_interactions_unavailable),
                Modifier.testTag("issue-interactions-unavailable"),
                style = AppType.secondary,
                color = AppTheme.colors.warn,
            )
        }
        if (state.thread.isEmpty()) {
            CardSurface(Modifier.testTag("issue-thread-empty")) {
                Text(
                    stringResource(R.string.issue_no_thread),
                    style = AppType.body,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        for (item in state.thread) {
            item.comment?.let { CommentCard(it) }
            item.interaction?.let { InteractionCard(it, state.isBusy, send) }
        }
    }
}

/** The line above a comment: "Engineer 1 · agent · 25m", "You · 4h". */
@Composable
fun commentAuthorLine(
    comment: IssueCommentUi,
    nowMillis: Long,
): String {
    val age = RelativeAge.of((nowMillis - comment.createdAtEpochMillis) / MILLIS_PER_SECOND).compactText()
    val agent = stringResource(CommentAuthorUi.Agent.stringRes())
    return when (comment.authorKind) {
        CommentAuthorUi.User -> {
            val who = if (comment.isMine) stringResource(CommentAuthorUi.User.stringRes()) else comment.authorName
            who?.let { stringResource(R.string.issue_author_pair, it, age) } ?: age
        }

        CommentAuthorUi.Agent -> {
            val name = comment.authorName ?: agent.replaceFirstChar { it.uppercase() }
            stringResource(R.string.issue_author_line, name, agent, age)
        }

        CommentAuthorUi.System -> {
            stringResource(R.string.issue_author_pair, stringResource(CommentAuthorUi.System.stringRes()), age)
        }
    }
}

@Composable
private fun CommentCard(comment: IssueCommentUi) {
    val colors = AppTheme.colors
    val now by rememberNowMillis()
    CardSurface(Modifier.semantics(mergeDescendants = true) {}.testTag("issue-comment-${comment.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            Text(commentAuthorLine(comment, now), style = AppType.label, color = colors.textSecondary)
            Text(comment.body, style = AppType.body, color = colors.textPrimary)
        }
    }
}
