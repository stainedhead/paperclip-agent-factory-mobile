package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.InteractionUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.VerdictUi

private val PendingBorder = 2.dp
private val SettledBorder = 1.dp

/**
 * One request from an agent (PRD FR-063). Which kind it is, whether it can be answered, why not, and whether the answer
 * is complete are the card's (the shared core's); the card draws the kind's controls and forwards each tap and edit.
 * Nothing here decides what a valid answer is.
 */
@Composable
fun InteractionCard(
    card: InteractionUi,
    isBusy: Boolean,
    send: (IssueIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    val pending = card.status == InteractionStatusUi.Pending
    val open = card.canAnswer && !isBusy
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                if (pending) PendingBorder else SettledBorder,
                if (pending) colors.accent else colors.hairline,
                shape,
            ).padding(Spacing.card)
            .testTag("interaction-${card.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(
            kindLine(card),
            Modifier.semantics { heading() }.testTag(card.tag("kind")),
            style = AppType.label,
            color = colors.accent,
        )
        card.title?.let {
            Text(it, Modifier.testTag(card.tag("title")), style = AppType.cardTitle, color = colors.textPrimary)
        }
        card.prompt?.takeIf { it != card.title }?.let {
            Text(it, Modifier.testTag(card.tag("prompt")), style = AppType.body, color = colors.textPrimary)
        }
        card.detailsMarkdown?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.testTag(card.tag("details")), style = AppType.body, color = colors.textSecondary)
        }
        Controls(card, open, send)
        if (pending) Footer(card, open, send)
    }
}

/** "QUESTIONS FOR YOU · 2 QUESTIONS", with the outcome once it is no longer waiting. */
@Composable
private fun kindLine(card: InteractionUi): String {
    val parts = mutableListOf(stringResource(card.kind.stringRes()))
    if (card.kind == InteractionKindUi.AskUserQuestions && card.questions.isNotEmpty()) {
        parts += pluralStringResource(R.plurals.issue_questions_count, card.questions.size, card.questions.size)
    }
    if (card.status != InteractionStatusUi.Pending) parts += stringResource(card.status.stringRes())
    return parts.joinToString(" · ").uppercase()
}

@Composable
private fun Controls(
    card: InteractionUi,
    open: Boolean,
    send: (IssueIntent) -> Unit,
) {
    SingleGroupChoices(card, open, send)
    for (question in card.questions) QuestionBlock(card, question, open, send)
    for (item in card.items) VerdictBlock(card, item, open, send)
    card.openInWebUrl?.let { OpenInWeb(card, it) }
}

/** Opens the issue in the web app. A refused or unopenable address says so on the card instead of doing nothing. */
@Composable
private fun OpenInWeb(
    card: InteractionUi,
    url: String,
) {
    val opener = currentWebOpener()
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        AppButton(
            stringResource(R.string.issue_open_in_web),
            { failed = !opener.open(url) },
            Modifier.testTag(card.tag("open-web")),
            kind = ButtonKind.Secondary,
        )
        if (failed) {
            Text(
                stringResource(R.string.issue_open_web_failed),
                Modifier.testTag(card.tag("open-web-failed")),
                style = AppType.secondary,
                color = AppTheme.colors.destructive,
            )
        }
    }
}

/** Why the card cannot be answered, or the buttons that answer it. */
@Composable
private fun Footer(
    card: InteractionUi,
    open: Boolean,
    send: (IssueIntent) -> Unit,
) {
    val reason = card.readOnlyReason
    if (reason != null) {
        Text(
            stringResource(reason.stringRes()),
            Modifier.testTag(card.tag("read-only")),
            style = AppType.secondary,
            color = AppTheme.colors.textSecondary,
        )
        return
    }
    if (card.hasReject) {
        val placeholder =
            stringResource(
                if (card.rejectRequiresReason) R.string.issue_reject_reason_required else R.string.issue_reject_reason,
            )
        DraftField(placeholder, card.rejectReason, card.tag("reject-reason"), open) {
            send(IssueIntent.InteractionTextChanged(card.id, "", it))
        }
    }
    card.primary?.let {
        AppButton(
            stringResource(it.stringRes()),
            { send(IssueIntent.InteractionPrimaryTapped(card.id)) },
            Modifier.testTag(card.tag("primary")),
            enabled = open && card.submitIssue == null,
        )
        card.submitIssue?.let { issue -> IssueText(card, "submit-issue", stringResource(issue.stringRes())) }
    }
    if (card.hasReject) {
        AppButton(
            stringResource(VerdictUi.Reject.stringRes()),
            { send(IssueIntent.InteractionRejectTapped(card.id)) },
            Modifier.testTag(card.tag("reject")),
            kind = ButtonKind.Destructive,
            enabled = open && card.rejectIssue == null,
        )
        card.rejectIssue?.let { issue -> IssueText(card, "reject-issue", stringResource(issue.stringRes())) }
    }
}

@Composable
private fun IssueText(
    card: InteractionUi,
    part: String,
    text: String,
) {
    Text(text, Modifier.testTag(card.tag(part)), style = AppType.secondary, color = AppTheme.colors.textSecondary)
}
