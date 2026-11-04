package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.AssigneeChoiceUi
import com.stainedhead.paperclipmobile.presentation.AssigneeKindUi
import com.stainedhead.paperclipmobile.presentation.IssueHeaderUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssueRefUi
import com.stainedhead.paperclipmobile.presentation.IssueState

private val MenuGlyph = 16.dp

/**
 * The top of the Issue screen, as in the mock: "PAP-1204 · project", the title, then status, priority and assignee as
 * pills. The assignee is a menu when this person may assign. Related issues (the parent, what blocks this) are links.
 */
@Composable
fun IssueHeader(
    state: IssueState,
    header: IssueHeaderUi,
    send: (IssueIntent) -> Unit,
    onOpenIssue: (String) -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        val identifier =
            header.projectName?.let { stringResource(R.string.issue_identifier_line, header.identifier, it) }
                ?: header.identifier
        Text(identifier, Modifier.testTag("issue-identifier"), style = AppType.label, color = colors.textSecondary)
        Text(
            header.title,
            Modifier.semantics { heading() }.testTag("issue-title"),
            style = AppType.title,
            color = colors.textPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                stringResource(header.status.stringRes()),
                header.status.tone(),
                Modifier.testTag("issue-status"),
            )
            StatusPill(
                stringResource(header.priority.stringRes()),
                header.priority.tone(),
                Modifier.testTag("issue-priority"),
            )
            AssigneeChip(state, header, send)
        }
        header.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.testTag("issue-summary"), style = AppType.body, color = colors.textPrimary)
        }
        header.parent?.let { RelatedIssue(R.string.issue_part_of, it, "issue-parent", onOpenIssue) }
        for (blocker in header.blockedBy) {
            RelatedIssue(R.string.issue_blocked_by, blocker, "issue-blocker-${blocker.id}", onOpenIssue)
        }
        UpdatedLabel(state.updatedAtEpochMillis)
    }
}

@Composable
private fun assigneeName(header: IssueHeaderUi): String =
    when {
        header.assigneeIsMe -> stringResource(R.string.issue_assignee_me)
        header.assigneeName != null -> header.assigneeName!!
        else -> stringResource(R.string.issue_assignee_unassigned)
    }

/** The assignee as a chip; a menu of who the issue can go to when the person may assign. */
@Composable
private fun AssigneeChip(
    state: IssueState,
    header: IssueHeaderUi,
    send: (IssueIntent) -> Unit,
) {
    val name = assigneeName(header)
    if (!state.canAssign) {
        AssigneePill(name, Modifier.testTag("issue-assignee"), onClick = null)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssigneePill(
            name,
            Modifier.testTag("issue-assignee-menu"),
            onClick = if (state.isBusy) null else ({ expanded = true }),
            onClickLabel = stringResource(R.string.issue_assignee_change),
        )
        DropdownMenu(expanded, { expanded = false }) {
            for (choice in state.assigneeChoices) {
                DropdownMenuItem(
                    text = { Text(choiceName(choice), style = AppType.body) },
                    onClick = {
                        expanded = false
                        send(IssueIntent.AssigneeSelected(choice.kind, choice.id))
                    },
                    modifier = Modifier.testTag("issue-assignee-${choice.id ?: "none"}"),
                )
            }
        }
    }
}

@Composable
private fun choiceName(choice: AssigneeChoiceUi): String =
    when (choice.kind) {
        AssigneeKindUi.None -> stringResource(R.string.issue_assignee_unassigned)
        AssigneeKindUi.Me -> stringResource(R.string.issue_assignee_me)
        AssigneeKindUi.Agent -> choice.name.orEmpty()
    }

@Composable
private fun AssigneePill(
    text: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onClickLabel: String? = null,
) {
    val colors = AppTheme.colors
    val shape = CircleShape
    Row(
        modifier
            .heightIn(min = if (onClick != null) Spacing.minimumTarget else 0.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ).padding(horizontal = Spacing.large, vertical = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(text, style = AppType.label, color = colors.textPrimary)
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                Modifier.size(MenuGlyph),
                tint = colors.textSecondary,
            )
        }
    }
}

/** A related issue as a link: "Part of: PAP-1182 · Billing v2". It is one 48 dp target that reads as a sentence. */
@Composable
private fun RelatedIssue(
    label: Int,
    ref: IssueRefUi,
    tag: String,
    onOpen: (String) -> Unit,
) {
    val title = stringResource(label)
    val description =
        stringResource(
            R.string.issue_link_description,
            title,
            ref.identifier,
            ref.title,
            stringResource(ref.status.stringRes()),
        )
    Text(
        stringResource(R.string.issue_link_text, title, ref.identifier, ref.title),
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .clickable(role = Role.Button) { onOpen(ref.id) }
            .padding(vertical = Spacing.medium)
            .testTag(tag)
            .semantics { contentDescription = description },
        style = AppType.body,
        color = AppTheme.colors.accent,
    )
}
