package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.ActionRow
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.ChoiceChip
import com.stainedhead.paperclipmobile.android.designsystem.EmptyState
import com.stainedhead.paperclipmobile.android.designsystem.FormField
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.StatusPill
import com.stainedhead.paperclipmobile.android.designsystem.UpdatedLabel
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueRowUi
import com.stainedhead.paperclipmobile.presentation.WorkListIntent
import com.stainedhead.paperclipmobile.presentation.WorkListState

private val SearchGlyph = 20.dp
private val Chevron = 16.dp
private val EmptyHeight = Spacing.minimumTarget * 5

/**
 * The search box. The text stays here while the person types and is sent when they submit it; clearing the box sends
 * an empty search at once so the filter shows again. What a search matches is the server's.
 */
@Composable
fun WorkSearchField(
    applied: String,
    onSearch: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var text by rememberSaveable { mutableStateOf(applied) }
    val searchLabel = stringResource(R.string.work_search_label)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.replace("\n", "")
            if (text.isEmpty() && applied.isNotEmpty()) onSearch("")
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minimumTarget)
                .testTag("work-search")
                .semantics { contentDescription = searchLabel },
        singleLine = true,
        textStyle = AppType.body.copy(color = colors.textPrimary),
        placeholder = {
            Text(stringResource(R.string.work_search_placeholder), style = AppType.body, color = colors.textSecondary)
        },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(SearchGlyph), tint = colors.textSecondary)
        },
        shape = RoundedCornerShape(Radius.button),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
        keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
            ),
    )
}

/** The saved filters as chips, in the mocks' order; the selected one is announced as selected. */
@Composable
fun FilterChips(
    selected: IssueFilterUi,
    onSelect: (IssueFilterUi) -> Unit,
) {
    val label = stringResource(R.string.work_filters_label)
    FlowRow(
        Modifier.fillMaxWidth().testTag("work-filters").semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for (filter in IssueFilterOrder.chips) {
            ChoiceChip(stringResource(filter.stringRes()), filter == selected, "work-filter-${filter.name}") {
                onSelect(filter)
            }
        }
    }
}

/** How many issues are shown and how old the read is, with the button that makes a new one when it is offered. */
@Composable
fun CountRow(
    state: WorkListState,
    onNewIssue: () -> Unit,
) {
    val count: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
            Text(
                pluralStringResource(R.plurals.work_count, state.rows.size, state.rows.size),
                Modifier.testTag("work-count"),
                style = AppType.secondary,
                color = AppTheme.colors.textSecondary,
            )
            UpdatedLabel(state.updatedAtEpochMillis)
        }
    }
    val create: @Composable (Modifier) -> Unit = { modifier ->
        AppButton(
            stringResource(R.string.work_create),
            onNewIssue,
            modifier.testTag("work-create"),
            icon = painterResource(R.drawable.ic_add),
        )
    }
    ActionRow(if (state.canCreate) listOf(count, create) else listOf(count))
}

@Composable
fun WorkEmpty(isSearch: Boolean) {
    EmptyState(
        icon = painterResource(R.drawable.ic_tab_work),
        title = stringResource(R.string.work_empty_title),
        message = stringResource(if (isSearch) R.string.work_empty_search_message else R.string.work_empty_message),
        modifier = Modifier.heightIn(min = EmptyHeight).semantics(mergeDescendants = true) {}.testTag("work-empty"),
    )
}

/** The end of the loaded rows: it asks for the next page once; the holder ignores a second ask. */
@Composable
fun LoadMoreRow(onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minimumTarget).testTag("work-loading-more"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(SearchGlyph), color = AppTheme.colors.accent, strokeWidth = 2.dp)
        Text(
            stringResource(R.string.work_loading_more),
            style = AppType.secondary,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** One issue: its number, title, status, priority and assignee, and a way in. The row reads as one sentence. */
@Composable
fun WorkRow(
    row: IssueRowUi,
    onOpen: () -> Unit,
    tagPrefix: String = "work-row",
) {
    val colors = AppTheme.colors
    val assignee = assigneeText(row)
    val sentence = rowSentence(row, assignee)
    CardSurface(
        Modifier
            .heightIn(min = Spacing.minimumTarget)
            .clickable(onClickLabel = stringResource(R.string.work_open_hint), role = Role.Button, onClick = onOpen)
            .testTag("$tagPrefix-${row.id}"),
    ) {
        Row(
            Modifier.clearAndSetSemantics { contentDescription = sentence },
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(row.identifier, style = AppType.label, color = colors.textSecondary)
                Text(row.title, style = AppType.cardTitle, color = colors.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    StatusPill(stringResource(row.status.stringRes()), row.status.tone())
                    StatusPill(stringResource(row.priority.stringRes()), row.priority.tone())
                }
                Text(assignee, style = AppType.secondary, color = colors.textSecondary)
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                Modifier.size(Chevron),
                tint = colors.textSecondary,
            )
        }
    }
}

/** What TalkBack reads for a row: "PAP-1204. Add retries. Blocked · High · Engineer 1". */
@Composable
private fun rowSentence(
    row: IssueRowUi,
    assignee: String,
): String {
    val summary =
        stringResource(
            R.string.work_row_summary,
            stringResource(row.status.stringRes()),
            stringResource(row.priority.stringRes()),
            assignee,
        )
    return stringResource(R.string.work_row_description, row.identifier, row.title, summary)
}

/** Who the issue is with: "You" when it is the person's own, the agent's name, or "Unassigned". */
@Composable
fun assigneeText(row: IssueRowUi): String =
    when {
        row.assignedToMe -> stringResource(R.string.work_assigned_to_you)
        row.assigneeName != null -> row.assigneeName!!
        else -> stringResource(R.string.work_unassigned)
    }

/**
 * "New issue": a title and a priority. The holder checks the title and the role; the sheet only collects them, and it
 * is closed by the screen once the issue exists, so a refusal or a failure keeps what was typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIssueSheet(
    state: WorkListState,
    send: (WorkListIntent) -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) { CreateIssueForm(state, send, onClose) }
}

@Composable
private fun CreateIssueForm(
    state: WorkListState,
    send: (WorkListIntent) -> Unit,
    onClose: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(IssuePriorityUi.Medium) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.page)
            .navigationBarsPadding()
            .imePadding()
            .testTag("work-create-form"),
        verticalArrangement = Arrangement.spacedBy(Spacing.roomy),
    ) {
        Text(
            stringResource(R.string.work_create_title),
            Modifier.semantics { heading() },
            style = AppType.title,
            color = AppTheme.colors.textPrimary,
        )
        FormField(
            title = stringResource(R.string.work_create_field),
            value = title,
            onValueChange = { title = it },
            tag = "work-create-title",
            placeholder = stringResource(R.string.work_create_placeholder),
            capitalization = KeyboardCapitalization.Sentences,
        )
        Text(stringResource(R.string.work_create_priority), style = AppType.button, color = AppTheme.colors.textPrimary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            for (choice in CREATE_PRIORITIES) {
                ChoiceChip(
                    stringResource(choice.stringRes()),
                    choice == priority,
                    "work-create-priority-${choice.name}",
                ) {
                    priority = choice
                }
            }
        }
        state.feedback?.let { FeedbackBanner(it, { send(WorkListIntent.MessageDismissed) }) }
        AppButton(
            stringResource(R.string.work_create_save),
            { send(WorkListIntent.CreateSubmitted(title, priority)) },
            Modifier.testTag("work-create-save"),
            enabled = !state.isCreating,
        )
        AppButton(
            stringResource(R.string.common_cancel),
            onClose,
            Modifier.testTag("work-create-cancel"),
            kind = ButtonKind.Secondary,
        )
    }
}

private val CREATE_PRIORITIES =
    listOf(IssuePriorityUi.Critical, IssuePriorityUi.High, IssuePriorityUi.Medium, IssuePriorityUi.Low)
